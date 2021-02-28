package precompute;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionWork;

import chain.Gadget;
import methodsEval.MethodInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NeoVisualize implements AutoCloseable {
	private final Driver driver;
	public static int id = 0;
	
	public NeoVisualize(String uri, String user, String password) {
		driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
//		indexGraph();
	}
	
	public void close() throws Exception {
		driver.close();
	}
	
	public void indexGraph() {
		try (Session session = driver.session()) {
			String query = "CREATE INDEX ID FOR (g:Gadget) ON (g.id)";
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
	
	private void createGadgets(Transaction tx, Gadget gadget) {
		List<Gadget> children = gadget.getRevisedChildren();
		if (gadget.getIsEntry()) {
			Map<String, Object> entryInfo = parseGadget(gadget);
			String query = 
					"CREATE (:Gadget {id:$id, class:$class, method:$method, methodType: $methodType, depth: $depth})";
			tx.run(query, entryInfo);	
		}
		final int parentId = gadget.getId();
		if (children == null) {
			return;
		}
	
		for (Gadget child : children) {
			Map<String, Object> NodeInfo = parseGadget(child);
			NodeInfo.put("parentId", parentId);
			String query =
					"MATCH (g:Gadget {id:$parentId})" + "\n" +
					"CREATE (g)-[:INVOKES {argumentsControlledByUser:$userControlledArguments}]->(c:Gadget)" + "\n" +
					"SET c.id = $id, c.class = $class, c.method = $method, c.methodType = $methodType, c.depth = $depth";		
			tx.run(query, NodeInfo);
		}
	}
	
	private static Map<String, Object> parseGadget(Gadget gadget) {
		Map<String, Object> params = new HashMap<>();
		params.put("id", id++);
		gadget.setId(id);
		String classname = gadget.getClazz().getCanonicalName();;
		params.put("class", classname);
		MethodInfo method = gadget.getMethod();
		String methodDesc = method.getName() + ":" + method.getDesc();
		String methodType = method.getMethodType()? "static" : "instance";
		params.put("methodType", methodType);
		params.put("method", methodDesc);
		List<Integer> userControlledArgPos = new ArrayList<Integer>();
		gadget.getUserContolledArgPos().forEach((k, v) -> {
			userControlledArgPos.add(k);
		});
		params.put("userControlledArguments", userControlledArgPos);	
		int depth = gadget.getDepth();
		params.put("depth", depth);
		
		return params;
	}
}
