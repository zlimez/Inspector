package chain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import chain.Enumerate.InvalidInputException;
import precompute.DbConnector;

public class Manager {
	public static void main(String[] args) throws ClassNotFoundException, IOException, InvalidInputException {
		if (!Files.exists(Paths.get("./JavaEnv.dat"))) {
			DbConnector.genJavaEnv();
		} else 
			Enumerate.runScan();
	}
}
