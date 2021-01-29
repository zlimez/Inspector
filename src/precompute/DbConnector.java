package precompute;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;

import chain.Blacklist;
import precompute.ReadSystem.StoreHierarchy;

/* user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * currently 8000 bytes reserved for hierarchy sufficient?
 * launch the database on cloud?
*/ 
public class DbConnector {
	private static Connection conn;
	
	public static void setConnector(String address) throws SQLException {
		if (!address.isBlank()) {
			conn = DriverManager.getConnection(address);
		} else {
			conn = DriverManager.getConnection("jdbc:mysql://locahost:3306/Inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		}
	}
	
	public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
		int option = Integer.parseInt(args[0]);
		if (option == 1) {
			try (
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(bos)
			) {
				int version = Integer.parseInt(args[1]);
				String pathToJDKClasslist = args[2];
				ReadSystem read = new ReadSystem(pathToJDKClasslist);
				StoreHierarchy hierarchy = read.readAndCreate();
				out.writeObject(hierarchy);
			    String data = Base64.getEncoder().encodeToString(bos.toByteArray());
				
				String sql = "insert into JavaSE values (?, ?)";
				PreparedStatement p = conn.prepareStatement(sql);
				p.setInt(1, version);
				p.setString(2, data);
				p.executeQuery(sql);
			} 
		} else if (option == 2) {
			String classname = args[1];
			String methodname = args[2];
			String sql = "insert into Blacklist values (?, ?)";
			PreparedStatement p = conn.prepareStatement(sql);
			p.setString(1, classname);
			p.setString(2, methodname);
			p.executeQuery();
		}
	}
	
	public static void initBlacklist() throws SQLException {
		String sql = "select * from Blacklist order by class";
		Statement s = conn.createStatement();
		ResultSet results = s.executeQuery(sql);
		String classname = "";
		while (results.next()) {
			if (!classname.equals(results.getString("class"))) {
				classname = results.getString("class");
				
			}
		}
	}
}
