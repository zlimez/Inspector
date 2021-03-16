package chain;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Scanner;

import chain.Enumerate.InvalidInputException;
import precompute.DbConnector;

public class Manager {
	public static String connection;
	public static String username;
	public static String password; 
	
	public static void main(String[] args) throws ClassNotFoundException, IOException, SQLException, InvalidInputException {
		Path currentDir = Paths.get(".");
		Path sqlConfigFile = currentDir.resolve("sqlConfigFile");
		if (Files.exists(sqlConfigFile)) {
			try (Scanner configDB = new Scanner(sqlConfigFile)) {
				String line;
				if (configDB.hasNextLine()) {
					line = configDB.nextLine();
				} else 
					throw new InvalidInputException("The mysqldb config file is empty");
				Scanner parser = new Scanner(line);
				parser.useDelimiter("\\s*\\,\\s*");
				connection = parser.next();
				username = parser.next();
				password = parser.next();
				parser.close();
			}
			int option = Integer.parseInt(args[0]);
			if (option == 1) {
				Enumerate.startScan();
			} else if (option == 2) {
				DbConnector.interact();
			} else 
				throw new InvalidInputException("No such option");
		} else {
			try (Scanner in = new Scanner(System.in)) {
				in.useDelimiter("\\n");
				System.out.println("Please provide the information required to connect with your local mysql db (eg. jdbc:mysql://localhost:3306/inspector?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC, James, Avarion0412*");
				String line = in.next();
				Scanner parser = new Scanner(line);
				parser.useDelimiter("\\s*\\,\\s*");
				connection = parser.next();
				username = parser.next();
				password = parser.next();
				parser.close();
				PrintWriter out = new PrintWriter(sqlConfigFile.toFile());
				out.print(connection + ", " + username + ", " + password);
				out.flush();
				out.close();
				System.out.println("Please restart the scanner to perform the scan");
			}
		}
	}
}
