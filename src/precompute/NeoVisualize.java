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
import java.util.List;
import java.util.Map;

public class NeoVisualize implements AutoCloseable {
	private final Driver driver;
	public static int id = 0;
	
	public NeoVisualize(String uri, String user, String password) {
		driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
	}
	
	public void close() throws Exception {
		driver.close();
	}
	
	public void indexGraph() {
		try (Session session = driver.session()) {
			String query = "CREATE CONSTRAINT UniqueID ON (g:Gadget) ASSERT g.id IS UNIQUE";
			session.run(query);
		}
	}
	
	public void genCallGraphFromThisNode (Gadget gadget) {
		try (Session session = driver.session()) {
			session.writeTransaction(new TransactionWork<Void>() {
				@Override 
				public Void execute (Transaction tx) {
					createGadgets(tx, gadget);
					return null;
				}
			});
		}
	}
	
	public void genInitialNode(Gadget first) {
		try (Session session = driver.session()) {
			Map<String, Object> entryInfo = parseGadget(first);
			String query = 
					"CREATE (:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})";
			session.run(query, entryInfo);	
		}
	}
	
	public void initializeID() {
		try (Session session = driver.session()) {
			String query = "MATCH (n) return count(*) as numOfNodes";
			Result result = session.run(query);
			id = result.single().get("numOfNodes").asInt();
		}
	}
	
	private void createGadgets(Transaction tx, Gadget gadget) {
		List<Gadget> children = gadget.getRevisedChildren();
		final int parentId = gadget.getId();
		if (children == null) {
			return;
		}
	
		for (Gadget child : children) {
			Map<String, Object> NodeInfo = parseGadget(child);
			NodeInfo.put("parentId", parentId);
			String query =
					"MATCH (g:Gadget {id:$parentId}) " +
					"CREATE (g)-[:INVOKES {argumentsControlledByUser:$userControlledArguments}]->(:Gadget {id:$id, class:$class, method:$method, methodType:$methodType, depth:$depth})";		
			tx.run(query, NodeInfo);
		}
	}
	
	private static Map<String, Object> parseGadget(Gadget gadget) {
		Map<String, Object> params = new HashMap<>();
		gadget.setId(id);
		params.put("id", id++);
		String classname = gadget.getClazz().getName();;
		params.put("class", classname);
		MethodInfo method = gadget.getMethod();
		String methodDesc = method.getName() + ":" + method.getDesc();
		String methodType = method.getIsStatic()? "static" : "instance";
		params.put("methodType", methodType);
		params.put("method", methodDesc);
		StringBuffer sb = new StringBuffer();
		gadget.getUserContolledArgPos().forEach((k, v) -> {
			sb.append(k + ",");
		});
		sb.deleteCharAt(sb.length() - 1);
		String userControlledArgPos = sb.toString();
		params.put("userControlledArguments", userControlledArgPos);	
		int depth = gadget.getDepth();
		params.put("depth", depth);
		
		return params;
	}
}
