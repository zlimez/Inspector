package precompute;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import precompute.ReadSystem.StoreHierarchy;

/* 
 * user can precompute hierarchy of different JRE system library to save time and edit blacklist 
 * launch the database on cloud?
*/ 
public class DbConnector {
	public static void main(String[] args) throws IOException, ClassNotFoundException {
		try (
			ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("./JavaEnv.dat")))
		) {
			StoreHierarchy hierarchy = ReadSystem.readJmods("C:/Program Files/AdoptOpenJDK/jdk-11.0.5.10-hotspot/jmods");
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
