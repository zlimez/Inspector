package precompute;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import chain.Gadget;
import methodsEval.MethodInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class NeoVisualize implements AutoCloseable {
	private final Driver driver;
	// nodes used to continue a scan
	private Set<Gadget> startNodes;
	private Set<Gadget> existingNodes;
	private LinkedList<Gadget> allNodes;
	private int maxDepth;
	private boolean isContinue;
	public static int id = 0;
	
	public NeoVisualize(String uri, String user, String password, Set<Gadget> all, int maxDepth, boolean isContinue, Set<Gadget> startNodes) {
		driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
		allNodes = new LinkedList<>(all);
		existingNodes = new HashSet<>();
		this.maxDepth = maxDepth;
		this.isContinue = isContinue;
		this.startNodes = startNodes;
		if (isContinue)
			existingNodes.addAll(startNodes);
	}
	
	public void close() throws Exception {
		driver.close();
	}
	
	public void initDB() {
		try (Session session = driver.session()) {
			if (isContinue) {
				Result result = session.run("MATCH (n) RETURN count(*) as numOfNodes");
				id = result.single().get("numOfNodes").asInt();
			} else 
				session.run("CREATE CONSTRAINT UniqueID ON (g:Gadget) ASSERT g.id IS UNIQUE");
		}
	}
	
	public void genGraph() {
		if (isContinue) {
			for (Gadget node : startNodes) {
				try (Session session = driver.session()) {
					session.writeTransaction(new TransactionWork<Void>() {
						@Override 
						public Void execute (Transaction tx) {
							removePrevEndpoints(tx, node);
							return null;
						}
					});
				}
			}
		}

		while (!allNodes.isEmpty()) {
			Gadget node = allNodes.getFirst();
			try (Session session = driver.session()) {
				session.writeTransaction(new TransactionWork<Void>() {
					@Override 
					public Void execute (Transaction tx) {
						genNodeRelations(tx, node);
						return null;
					}
				});
			}
		}	
	}
	
	private void removePrevEndpoints(Transaction tx, Gadget node) {
		Map<String, Object> info = new HashMap<>();
		int nodeID = node.getId();
		info.put("nodeID", nodeID);
		String query = "MATCH (n {id:$nodeID}) REMOVE n:Endpoint";
		tx.run(query, info);
	}
	
	private void genNodeRelations(Transaction tx, Gadget node) {
		//limit query complexity
		if (!existingNodes.contains(node)) {
			existingNodes.add(node);
			Map<String, Object> info = new HashMap<>();
			parseGadget("", node, info);
			StringBuffer query = new StringBuffer();
			if (node.getIsSink()) {
				query.append("CREATE (n:Sink:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})");
			} else if (node.getMethod().getIsField()) {
				if (node.getDepth() == maxDepth) {
					query.append("CREATE (n:Field:Endpoint:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})");
				} else
					query.append("CREATE (n:Field:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})");	
			} else {
				if (node.getDepth() == maxDepth) {
					query.append("CREATE (n:Endpoint:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})");
				} else
					query.append("CREATE (n:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})");
			}
			Stack<Gadget> children = node.getRevisedChildren();
			if (children != null && !children.isEmpty()) {
				int i = 1;
				while (true) {
					Gadget child = children.pop();
					query.append(" WITH n");
					if (existingNodes.contains(child)) {
						int childID = child.getId();
						info.put("child" + i + "ID", childID);
						StringBuffer sb = new StringBuffer();
						child.getUserContolledArgPos().forEach((k, v) -> {
							sb.append("["+ k + v + "]");
						});
						String userControlledArgPos = sb.toString();
						info.put("userControlledArguments" + i, userControlledArgPos);
						query.append(" MATCH (c {id:$child"+ i + "ID}) CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(c)");
					} else {
						existingNodes.add(child);
						parseGadget("child" + i, child, info);
						StringBuffer sb = new StringBuffer();
						child.getUserContolledArgPos().forEach((k, v) -> {
							sb.append("["+ k + " " + v + "]");
						});
						String userControlledArgPos = sb.toString();
						info.put("userControlledArguments" + i, userControlledArgPos);
						if (child.getIsSink()) {
							query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Sink:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
						} else if (child.getMethod().getIsField()) {
							if (child.getDepth() == maxDepth) {
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Field:Endpoint:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
							} else
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Field:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");	
						} else {
							if (child.getDepth() == maxDepth) {
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Endpoint:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
							} else
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
						}
					}
					if (children.isEmpty()) {
						allNodes.removeFirst();
						break;
					}
					if (++i == 50)
						break;
				}
			} else 
				allNodes.removeFirst();
			tx.run(query.toString(), info);
		} else {
			Stack<Gadget> children = node.getRevisedChildren();
			if (children != null && !children.isEmpty()) {
				Map<String, Object> info = new HashMap<>();
				StringBuffer query = new StringBuffer();
				int nodeID = node.getId();
				info.put("nodeID", nodeID);
				query.append("MATCH (n {id:$nodeID})");
				int i = 1;
				while (true) {
					Gadget child = children.pop();
					query.append(" WITH n");
					if (existingNodes.contains(child)) {
						int childID = child.getId();
						info.put("child" + i + "ID", childID);
						StringBuffer sb = new StringBuffer();
						child.getUserContolledArgPos().forEach((k, v) -> {
							sb.append("["+ k + v + "]");
						});
						String userControlledArgPos = sb.toString();
						info.put("userControlledArguments" + i, userControlledArgPos);
						query.append(" MATCH (c {id:$child"+ i + "ID}) CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(c)");
					} else {
						existingNodes.add(child);
						parseGadget("child" + i, child, info);
						StringBuffer sb = new StringBuffer();
						child.getUserContolledArgPos().forEach((k, v) -> {
							sb.append("["+ k + v + "]");
						});
						String userControlledArgPos = sb.toString();
						info.put("userControlledArguments" + i, userControlledArgPos);
						if (child.getIsSink()) {
							query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Sink:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
						} else if (child.getMethod().getIsField()) {
							if (child.getDepth() == maxDepth) {
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Field:Endpoint:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
							} else
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Field:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");	
						} else {
							if (child.getDepth() == maxDepth) {
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Endpoint:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");	
							} else
								query.append(" CREATE (n)-[:INVOKES {argumentsControlledByUser:$userControlledArguments" + i + "}]->(:Gadget {id:$child" + i + "id, class:$child" + i + "class, method:$child" + i + "method, methodType:$child" + i + "methodType, depth:$child" + i + "depth})");
						}
					}
					if (children.isEmpty()) {
						allNodes.removeFirst();
						break;
					}
					if (++i == 50)
						break;
				}
				tx.run(query.toString(), info);
			} else
				allNodes.removeFirst();
		}
	}
	
	private static Map<String, Object> parseGadget(String prefix, Gadget gadget, Map<String, Object> params) {
		gadget.setId(id);
		params.put(prefix + "id", id++);
		params.put(prefix + "class", gadget.getClassname());
		MethodInfo method = gadget.getMethod();
		String methodDesc = method.getName() + ":" + method.getDesc();
		String methodType = method.getIsStatic()? "static" : "instance";
		params.put(prefix + "methodType", methodType);
		params.put(prefix + "method", methodDesc);
		params.put(prefix + "depth", gadget.getDepth());
		return params;
	}
}
