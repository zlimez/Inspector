package main;

import hierarchy.SortClass;
import methodsEval.MethodInfo;
import methodsEval.RenderClass;
import chain.serialcheck.CheckForSerialization;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.analysis.BasicValue;

import chain.Entry;
import userFields.UserFieldInterpreter;

public class AllTest {
	public static void main(String[] args) throws ClassNotFoundException, IOException {
		SortClass sort = new SortClass("/home/pcadmin/Deserialization/VulnServlet.war");
		sort.getSerialAndAllClasses();
		
		// determine the fields that can be determined by a user in a class
		
		try (
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			BufferedInputStream in = new BufferedInputStream(new FileInputStream("/home/pcadmin/eclipse-workspace/Miscellaneous/bin/main/ControlFlowRef.class"));
		) {
			Map<Integer, BasicValue> arguments = new HashMap<Integer, BasicValue>();
			arguments.put(1, UserFieldInterpreter.USER_INFLUENCED);
     		arguments.put(2, UserFieldInterpreter.USER_DERIVED);
			byte[] byteBuffer = new byte[4096];
			int byteread;
			while ((byteread = in.read(byteBuffer)) != -1) {
				bos.write(byteBuffer, 0, byteread);
			}
			byte[] testBytes = bos.toByteArray();
			ClassReader cr = new ClassReader(testBytes);
			String owner = cr.getClassName();
			ClassWriter cw = new ClassWriter(cr, 0);
			RenderClass rc = new RenderClass(cw, owner, "Reference", arguments);
			cr.accept(rc, 0);
			List<MethodInfo> ms = rc.getNextInvokedMethods();
			System.out.println(ms.get(0).getName());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
