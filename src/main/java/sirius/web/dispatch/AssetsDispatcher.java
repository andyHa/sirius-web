/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.dispatch;

import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.serversass.Generator;
import org.serversass.Output;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.PriorityCollector;
import sirius.kernel.commons.Strings;
import sirius.kernel.commons.Tuple;
import sirius.kernel.di.std.ConfigValue;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.PriorityParts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Exceptions;
import sirius.kernel.health.Log;
import sirius.kernel.info.Product;
import sirius.web.http.WebContext;
import sirius.web.http.WebDispatcher;
import sirius.web.security.UserContext;
import sirius.web.templates.Content;
import sirius.web.templates.Resolver;
import sirius.web.templates.Resource;

import java.io.*;
import java.net.URL;
import java.util.List;
import java.util.Optional;

/**
 * Dispatches all URLs below <code>/assets</code>.
 * <p>
 * All assets are fetched from the classpath and should be located in the <tt>resources</tt> source root (below the
 * <tt>assets</tt> directory).
 * <p>
 * This dispatcher tries to support caching as well as zero-copy delivery of static files if possible.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/11
 */
@Register
public class AssetsDispatcher implements WebDispatcher {

    @Override
    public int getPriority() {
        return PriorityCollector.DEFAULT_PRIORITY;
    }

    @Override
    public boolean preDispatch(WebContext ctx) throws Exception {
        return false;
    }

    @ConfigValue("http.generated-directory")
    private String cacheDir;
    private File cacheDirFile;

    @PriorityParts(Resolver.class)
    private List<Resolver> resolvers;

    @Override
    public boolean dispatch(WebContext ctx) throws Exception {
        if (!ctx.getRequest().getUri().startsWith("/assets") || HttpMethod.GET != ctx.getRequest().getMethod()) {
            return false;
        }
        String uri = ctx.getRequestedURI();
        if (uri.startsWith("/assets/dynamic")) {
            uri = uri.substring(16);
            Tuple<String, String> pair = Strings.split(uri, "/");
            uri = "/assets/" + pair.getSecond();
        }
        Optional<Resource> res = content.resolve(uri);
        if (res.isPresent()) {
            URL url = res.get().getUrl();
            if ("file".equals(url.getProtocol())) {
                ctx.respondWith().file(new File(url.toURI()));
            } else {
                ctx.respondWith().resource(url.openConnection());
            }
            return true;
        }

        String scopeId = UserContext.getCurrentScope().getScopeId();

        // If the file is not found not is a .css file, check if we need to generate it via a .scss file
        if (uri.endsWith(".css")) {
            String scssUri = uri.substring(0, uri.length() - 4) + ".scss";
            if (content.resolve(scssUri).isPresent()) {
                handleSASS(ctx, uri, scssUri, scopeId);
                return true;
            }
        }
        // If the file is non existent, check if we can generate it by using a velocity template
        if (content.resolve(uri + ".vm").isPresent()) {
            handleVM(ctx, uri, scopeId);
            return true;
        }
        return false;
    }

    private static final Log SASS_LOG = Log.get("sass");

    /*
     * Subclass of generator which takes care of proper logging
     */
    private class SIRIUSGenerator extends Generator {
        @Override
        public void debug(String message) {
            SASS_LOG.FINE(message);
        }

        @Override
        public void warn(String message) {
            SASS_LOG.WARN(message);
        }


        @Override
        protected InputStream resolveIntoStream(String sheet) throws IOException {
            Optional<Resource> res = content.resolve(sheet);
            if (res.isPresent()) {
                return res.get().getUrl().openStream();
            }
            return null;
        }

    }

    @Part
    private Content content;

    /*
     * Uses Velocity (via the content generator) to generate the desired file
     */
    private void handleVM(WebContext ctx, String uri, String scopeId) throws IOException {
        String cacheKey = scopeId + "-" + uri.substring(1).replaceAll("[^a-zA-Z0-9_\\.]", "_");
        File file = new File(getCacheDirFile(), cacheKey);

        if (!file.exists() || file.lastModified() < content.resolve(uri + ".vm").get().getLastModified()) {
            try {
                if (Sirius.isDev()) {
                    Content.LOG.INFO("Compiling: " + uri + ".vm");
                }
                FileOutputStream out = new FileOutputStream(file, false);
                content.generator().useTemplate(uri + ".vm").generateTo(out);
                out.close();
            } catch (Throwable t) {
                file.delete();
                ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(Content.LOG, t));
            }
        }

        ctx.respondWith().named(uri.substring(uri.lastIndexOf("/") + 1)).file(file);
    }

    /*
     * Uses server-sass to compile a SASS file (.scss) into a .css file
     */
    private void handleSASS(WebContext ctx, String cssUri, String scssUri, String scopeId) throws IOException {
        String cacheKey = scopeId + "-" + cssUri.substring(1).replaceAll("[^a-zA-Z0-9_\\.]", "_");
        File file = new File(getCacheDirFile(), cacheKey);

        if (!file.exists() || content.resolve(scssUri).get().getLastModified() - file.lastModified() > 5000) {
            if (Sirius.isDev()) {
                SASS_LOG.INFO("Compiling: " + scssUri);
            }
            try {
                SIRIUSGenerator gen = new SIRIUSGenerator();
                gen.importStylesheet(scssUri);
                gen.compile();
                FileWriter writer = new FileWriter(file, false);
                // Let the content compressor take care of minifying the CSS
                Output out = new Output(writer, false);
                gen.generate(out);
                writer.close();
            } catch (Throwable t) {
                file.delete();
                ctx.respondWith().error(HttpResponseStatus.INTERNAL_SERVER_ERROR, Exceptions.handle(SASS_LOG, t));
            }
        }

        ctx.respondWith().named(cssUri.substring(cssUri.lastIndexOf("/") + 1)).file(file);
    }

    /*
     * Resolves the directory used to cache the generated files
     */
    private File getCacheDirFile() {
        if (cacheDirFile == null) {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"),
                                   Product.getProduct()
                                          .getName()
                                          .replaceAll("[a-zA-Z0-9\\-]", "_") + "_" + CallContext.getNodeName() + "_" + cacheDir);
            tmpDir.mkdirs();
            cacheDirFile = tmpDir;
        }
        return cacheDirFile;
    }
}
