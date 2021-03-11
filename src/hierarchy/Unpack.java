package hierarchy;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import hierarchy.SortClass.CustomLoader;

public class Unpack {
	public static Path warDir;
	
	public static void initialize(String ... dest) throws IOException {
		if (dest.length == 1)
			warDir = Files.createDirectory(Paths.get(dest[0]));
		else 
			warDir = Files.createDirectory(Paths.get(".").resolve("Sample"));
	}
	// Return classloader for jar file entries in lib directory to feed to reflections
	public static CustomLoader getLibLoader(Path warPath) throws IOException {
        
        // Extract to war to the temp directory
		decompress(warPath, warDir);
		
        final List<URL> classPathUrls = new ArrayList<>();
        Files.list(warDir.resolve("WEB-INF/lib")).forEach(p -> {
            try {
                classPathUrls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
            try {
				decompress(p, warDir.resolve("WEB-INF/lib"));
			} catch (IOException e) {
				e.printStackTrace();
			}
        });
        
        CustomLoader warURLs = new CustomLoader(classPathUrls.toArray(new URL[classPathUrls.size()]));
        return warURLs;
	}
	
	public static CustomLoader genericResourceLoader(URLClassLoader loader, String serverPath) throws IOException {
		Path server = Paths.get(serverPath);
		List<URL> urls = new ArrayList<>();
		for (URL url: loader.getURLs()) {
			urls.add(url);
		}
		Files.list(server).forEach(p -> {
			try {
				urls.add(p.toUri().toURL());
			} catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
		});
		CustomLoader genericLoader = new CustomLoader(urls.toArray(new URL[urls.size()]));
		return genericLoader;
	}
	
	private static void decompress(Path file, Path dest) throws IOException {
	  try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(file))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = dest.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }
                    try (OutputStream outputStream = Files.newOutputStream(fullPath)) {
                        copy(jarInputStream, outputStream);
                    }
                }
            }
        }
	}
	
	// Return byte array of the user classes
	public static Object[] getClassesPath() throws IOException {
		Object[] URLAndClass = new Object[2];
		List<byte[]> clazzes = new ArrayList<>();
		Path root = warDir.resolve("WEB-INF/classes");
		URL userClassDir = root.toUri().toURL();
   		String filter = ".class$";
		Pattern pattern = Pattern.compile(filter);
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
        	@Override
        	public FileVisitResult visitFile(Path filename, BasicFileAttributes attr) {
        		Matcher matcher = pattern.matcher(filename.toString());
        		if (matcher.find()) {
        			try (
    					ByteArrayOutputStream bos = new ByteArrayOutputStream();
    					BufferedInputStream in = new BufferedInputStream(new FileInputStream(filename.toFile()));
    				) {
    					byte[] byteBuffer = new byte[4096];
    					int byteread;
    					while ((byteread = in.read(byteBuffer)) != -1) {
    						bos.write(byteBuffer, 0, byteread);
    					}
    					byte[] clazzBytes = bos.toByteArray();
    					clazzes.add(clazzBytes);
    				} catch (IOException e) {
    					e.printStackTrace();
    				}
        			return FileVisitResult.CONTINUE;
        		} else {
        			return FileVisitResult.CONTINUE;
        		}
        	}
        });
        URLAndClass[0] = userClassDir;
		URLAndClass[1] = clazzes;
		return URLAndClass;
	}

    public static CustomLoader getJarClassLoader(Path[] jarPaths) throws IOException {
        final List<URL> classPathUrls = new ArrayList<>(jarPaths.length);
        for (Path jarPath : jarPaths) {
            if (!Files.exists(jarPath) || Files.isDirectory(jarPath)) {
                throw new IllegalArgumentException("Path \"" + jarPath + "\" is not a path to a file.");
            }
            classPathUrls.add(jarPath.toUri().toURL());
            decompress(jarPath, warDir);
        }
        CustomLoader classLoader = new CustomLoader(classPathUrls.toArray(new URL[classPathUrls.size()]));
        return classLoader;
    }

    /**
     * Recursively delete the directory root and all its contents
     * @param root Root directory to be deleted
     */
    public static void deleteDirectory() throws IOException {
        Files.walkFileTree(warDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Copy inputStream to outputStream. Neither stream is closed by this method.
     */
    private static void copy(InputStream inputStream, OutputStream outputStream) throws IOException {
        final byte[] buffer = new byte[4096];
        int n;
        while ((n = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, n);
        }
    }
}