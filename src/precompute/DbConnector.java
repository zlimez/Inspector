package precompute;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Scanner;

import precompute.ReadSystem.StoreHierarchy;

/* 
 * user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * launch the database on cloud?
*/ 
public class DbConnector {
	public static void genJavaEnv() throws IOException, ClassNotFoundException {
		try (
			Scanner in = new Scanner(System.in);
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("./JavaEnv.dat")))
		) {
			System.out.println("Provide the path to the jmods directory of your JDK installation (eg. C:/Program Files/AdoptOpenJDK/jdk-11.0.5.10-hotspot/jmods)");
			in.useDelimiter("\\r|\\n");
			StoreHierarchy hierarchy = ReadSystem.readJmods(in.next());
			out.writeObject(hierarchy);
		} 
	}
	
	public static StoreHierarchy getSystemInfo() {
		try (
			ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(new FileInputStream("./JavaEnv.dat")));
		) {
			StoreHierarchy stored = (StoreHierarchy) in.readObject();
			return stored;
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}
}
