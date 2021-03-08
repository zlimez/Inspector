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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

import chain.Blacklist;
import chain.Enumerate.InvalidInputException;

/* 
 * user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * launch the database on cloud?
*/ 
public class DbConnector {
	public static void main(String[] args) throws SQLException, ClassNotFoundException, IOException, InvalidInputException {
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		try (Scanner in = new Scanner(System.in)) {
			System.out.println("Do you wish to insert the hierarchy and data of the classes that belong to a Java version missing in the JavaSE table? (Y/N");
			String choice = in.next();
			if (choice.equalsIgnoreCase("Y")) {
				System.out.println("Specify the version of java your data will belongs to");
				int version = in.nextInt();
				System.out.println("Do you wish to generate the data by \n\t 1.using a text file containing all the classes of the specified java version of which the env your tool is running in as well \n\t 2. providing the path to the rt.jar file of the specified java version of which can be different from the env your tool is running in? (1/2)");
				int option = in.nextInt();
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
						
						String sql = "INSERT INTO JavaSE values (?, ?)";
						PreparedStatement p = conn.prepareStatement(sql);
						p.setInt(1, version);
						p.setString(2, data);
						p.executeUpdate();
					} 
				} else if (option == 2) {
					// do something wit rt.jar
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
	
//	public static void initBlacklist(List<String> libs, List<Integer> version) throws SQLException { // index i at both math to form a pair lib and its version
//		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
//	    StringBuffer concatlibs = new StringBuffer("(");
//	    for (int i = 0; i < libs.size(); i++) {
//	    	concatlibs.append("\'" + libs.get(i) + "\'" + ",");  	
//	    }
//	    concatlibs.deleteCharAt(concatlibs.length() - 1);
//	    concatlibs.append(")");
//		String sql = "SELECT class, method, descriptor FROM Blacklist WHERE library IN " + concatlibs + " order by class, method, descriptor";
//	}
	
	public static StoreHierarchy getSystemInfo(int version) throws SQLException {
		Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC", "James", "Avarion0412*");
		String sql = "SELECT classes FROM JavaSE WHERE version = ?";
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
