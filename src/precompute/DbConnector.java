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
import java.util.Scanner;

import chain.Blacklist;
import chain.Enumerate.InvalidInputException;
import chain.Manager;

/* 
 * user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * launch the database on cloud?
*/ 
public class DbConnector {
	public static void interact() throws SQLException, ClassNotFoundException, IOException, InvalidInputException {
		Connection conn = DriverManager.getConnection(Manager.connection, Manager.username, Manager.password);
		try (Scanner in = new Scanner(System.in)) {
			System.out.println("Do you wish to insert the hierarchy and data of the classes that belong to a Java version missing in the JavaSE table? (Y/N");
			String choice = in.next();
			if (choice.equalsIgnoreCase("Y")) {
				System.out.println("Specify the version of java your data will belongs to");
				int version = in.nextInt();
				System.out.println("Do you wish to generate the data by \n\t 1.using a text file containing all the classes of the specified java version \n\t 2. providing the path to the rt.jar file of the specified java version? (1/2)");
				int option = in.nextInt();
				System.out.println("Provide the path to either the classlist file or rt.jar file");
				String pathToFile = in.next();
				if (option == 1) {
					try (
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						ObjectOutputStream out = new ObjectOutputStream(bos)
					) {
						StoreHierarchy hierarchy = ReadSystem.readAndCreate(pathToFile);
						out.writeObject(hierarchy);
					    String data = Base64.getEncoder().encodeToString(bos.toByteArray());
						
						String sql = "INSERT INTO JavaSE values (?, ?)";
						PreparedStatement p = conn.prepareStatement(sql);
						p.setInt(1, version);
						p.setString(2, data);
						p.executeUpdate();
					} 
				} else if (option == 2) {
					try (
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						ObjectOutputStream out = new ObjectOutputStream(bos)
					) {
						StoreHierarchy hierarchy = ReadSystem.readRtJar(pathToFile);
						out.writeObject(hierarchy);
					    String data = Base64.getEncoder().encodeToString(bos.toByteArray());
						
						String sql = "INSERT INTO JavaSE values (?, ?)";
						PreparedStatement p = conn.prepareStatement(sql);
						p.setInt(1, version);
						p.setString(2, data);
						p.executeUpdate();
					} 
				} else {
					throw new InvalidInputException("Invalid Input");
				}
			} else if (choice.equalsIgnoreCase("N")) {
			} else {
				throw new InvalidInputException("Invalid Input");
			}
		}
	}
	
	public static void initBlacklist(int version) throws SQLException {
		Connection conn = DriverManager.getConnection(Manager.connection, Manager.username, Manager.password);
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
		Connection conn = DriverManager.getConnection(Manager.connection, Manager.username, Manager.password);
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
