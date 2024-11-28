package nl.scuro.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Hello world!
 *
 */
public class SimpleWebserver {
    private final static Logger LOGGER = Logger.getLogger(SimpleWebserver.class.getName());

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String[] args) throws IOException {
        Integer port = 8181;
        if (args.length > 0) {
            port = Integer.valueOf(args[0]);
        }
        LOGGER.log(Level.INFO, "Starting simple HTTP web server on port {0}", port.toString());
        HttpServer httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/", new FileHandler());
        httpServer.start();
    }

    private static class FileHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange httpExchange) throws IOException {
            URI uri = httpExchange.getRequestURI();
            LOGGER.info("requested: " + uri);
            final String path = uri.getPath();
            File file = new File("." + path);
            if (!file.exists()) {
                httpExchange.sendResponseHeaders(404, 0);
                httpExchange.close();
                LOGGER.info(uri + " not found. Return 404");
                return;
            }

            if (file.isDirectory() && !path.endsWith("/")) {
                httpExchange.getResponseHeaders().add("location", path + "/");
                httpExchange.sendResponseHeaders(302, 0);
                httpExchange.close();
                return;
            }

            try (OutputStream os = httpExchange.getResponseBody()) {
                if (file.isFile()) {
                    writeFileToResponse(file, httpExchange, os);
                } else {
                    writeDirectoryListingToResponse(file, httpExchange, os, path);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void writeFileToResponse(File file, HttpExchange httpExchange, final OutputStream os)
                throws FileNotFoundException, IOException {
            long size = file.length();
            String contentType = Files.probeContentType(file.toPath());
            if (contentType != null) {
                httpExchange.getResponseHeaders().add("Content-Type", contentType);
            }
            httpExchange.sendResponseHeaders(200, size);
            byte[] buffer = new byte[8 * 1024];
            int len;
            try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file))) {
                while ((len = in.read(buffer)) > 0) {
                    os.write(buffer, 0, len);
                }
            }
        }

        private void writeDirectoryListingToResponse(File file, HttpExchange httpExchange, final OutputStream os,
                String path) throws FileNotFoundException, IOException {
            File[] listFiles = file.listFiles();
            StringBuilder sb = new StringBuilder("<html><h1>Listing for ");
            sb.append(path);
            sb.append("</h1>");
            sb.append("<a href='../'>../</><br>");
            for (File listFile : listFiles) {
                sb.append("<a href='");
                sb.append(path);
                final String fileName = provideFileName(listFile);
                sb.append(fileName);
                sb.append("'>");
                sb.append(fileName);
                sb.append("</a>");
                sb.append("<br>");
            }
            httpExchange.getResponseHeaders().add("Content-Type", "text/html");
            httpExchange.sendResponseHeaders(200, sb.length());
            os.write(sb.toString().getBytes());
        }

        private String provideFileName(File file) {
            StringBuilder name = new StringBuilder(file.getName());
            if (file.isDirectory()) {
                name.append('/');
            }
            return name.toString();
        }
    }

}
