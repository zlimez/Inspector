package precompute;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

import chain.Blacklist;

/* 
 * user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * launch the database on cloud?
*/ 
public class DbConnector {
	public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException {
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		int option = Integer.parseInt(args[0]);
		int version = Integer.parseInt(args[1]);
		if (option == 1) {
			try (
				ByteArrayOutputStream bos = new ByteArrayOutputStream();
				ObjectOutputStream out = new ObjectOutputStream(bos)
			) {
				String pathToJDKClasslist = args[2];
				ReadSystem read = new ReadSystem(pathToJDKClasslist);
				StoreHierarchy hierarchy = read.readAndCreate();
				out.writeObject(hierarchy);
			    String data = Base64.getEncoder().encodeToString(bos.toByteArray());
				
				String sql = "insert into JavaSE values (?, ?)";
				PreparedStatement p = conn.prepareStatement(sql);
				p.setInt(1, version);
				p.setString(2, data);
				p.executeUpdate();
			} 
		} else if (option == 2) {
			int action = Integer.parseInt(args[2]);
			if (action == 1) {
				String classname = args[3];
				String methodname = args[4];
				String desc = args[5];
				String sql = "insert into Blacklist values (NULL, ?, ?, ?, ?)";
				PreparedStatement p = conn.prepareStatement(sql);
				p.setInt(1, version);
				p.setString(2, classname);
				p.setString(3, methodname);
				p.setString(4, desc);
				p.executeUpdate();
			}
		}
	}
	
	public static void initBlacklist(int version) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		String sql = "select class, method, descriptor from Blacklist where version = ? order by class, method, descriptor";
		PreparedStatement p = conn.prepareStatement(sql);
		p.setInt(1, version);
		ResultSet results = p.executeQuery();
		while (results.next()) {
			String classname = results.getString("class");
			String method = results.getString("method");
			String desc = results.getString("descriptor");
			Blacklist.changeList(classname, method, desc);
		} 
	}
	
	public static StoreHierarchy getSystemInfo(int version) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		String sql = "select classes from JavaSE where version = ?";
		PreparedStatement p = conn.prepareStatement(sql);
		p.setInt(1, version);
		ResultSet result = p.executeQuery();
		if (!result.next()) {
			System.out.println("This java version is not available in database yet");
		} else {
			byte[] data = Base64.getDecoder().decode(result.getString("classes"));
			try (
				ByteArrayInputStream bis = new ByteArrayInputStream(data);
				ObjectInputStream in = new ObjectInputStream(bis);
			) {
				StoreHierarchy stored = (StoreHierarchy) in.readObject();
				return stored;
			} catch (IOException | ClassNotFoundException e) {
				e.printStackTrace();
			}
		}

		return null;
	}
}
