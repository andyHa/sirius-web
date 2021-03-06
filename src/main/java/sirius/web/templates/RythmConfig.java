/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.templates;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.rythmengine.Rythm;
import org.rythmengine.RythmEngine;
import org.rythmengine.conf.RythmConfigurationKey;
import org.rythmengine.extension.II18nMessageResolver;
import org.rythmengine.extension.ISourceCodeEnhancer;
import org.rythmengine.resource.ITemplateResource;
import org.rythmengine.resource.ResourceLoaderBase;
import org.rythmengine.template.ITemplate;
import org.rythmengine.template.JavaTagBase;
import sirius.kernel.Lifecycle;
import sirius.kernel.Sirius;
import sirius.kernel.async.CallContext;
import sirius.kernel.commons.Strings;
import sirius.kernel.di.std.Part;
import sirius.kernel.di.std.Parts;
import sirius.kernel.di.std.Register;
import sirius.kernel.health.Log;
import sirius.kernel.info.Product;
import sirius.kernel.nls.NLS;
import sirius.web.http.WebContext;
import sirius.web.security.UserContext;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Initializes and configures Rythm (http://www.rythmengine.org).
 * <p>
 * Configures Rythm, so that {@literal @}i18n uses {@link NLS#get(String)}. Also the template lookup is changed
 * to scan resources/view/... or resources/help/...
 * <p>
 * Each template will have two auto-import: {@link NLS} and {@link sirius.kernel.commons.Strings}. Additionally,
 * the following variables are declared:
 * <ul>
 * <li><b>ctx</b>: the current {@link CallContext}</li>
 * <li><b>user</b>: the current {@link UserContext}</li>
 * <li><b>prefix</b>: the http url prefix</li>
 * <li><b>product</b>: the name of the product</li>
 * <li><b>version</b>: the version of the product</li>
 * <li><b>detailedVersion</b>: the detailed version of the product</li>
 * <li><b>isDev</b>: <tt>true</tt> if the system is started in development mode, <tt>false</tt> otherwise</li>
 * <li><b>call</b>: the current {@link WebContext}</li>
 * <li><b>template</b>: the name of the template currently being rendered</li>
 * <li><b>lang</b>: the current language</li>
 * <li><b>dateFormat</b>: the date format for the current language</li>
 * <li><b>timeFormat</b>: the time format for the current language</li>
 * </ul>
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/11
 */
@Register
public class RythmConfig implements Lifecycle {

    public static final Log LOG = Log.get("rythm");

    /*
     * Adapter to make @i18n commands use NLS.get
     */
    public static class I18nResourceResolver implements II18nMessageResolver {

        @Override
        public String getMessage(ITemplate template, String key, Object... args) {
            return NLS.apply(key, args);
        }
    }

    @Parts(RythmExtension.class)
    private Collection<RythmExtension> extensions;

    @Part
    private Content content;


    @Override
    public void started() {
        Map<String, Object> config = Maps.newTreeMap();
        // We always put Rythm in dev mode to support dynamic reloading of templates...
        config.put("rythm.engine.mode", "dev");
        File tmpDir = new File(System.getProperty("java.io.tmpdir"),
                               Product.getProduct()
                                      .getName()
                                      .replaceAll("[a-zA-Z0-9\\-]", "_") + "_" + CallContext.getNodeName() + "_rythm");
        tmpDir.mkdirs();
        if (Sirius.isDev()) {
            if (tmpDir.listFiles() != null) {
                for (File file : tmpDir.listFiles()) {
                    if (file.getName().endsWith(".java") || file.getName().endsWith(".rythm")) {
                        file.delete();
                    }
                }
            }
        }
        config.put("rythm.home.tmp.dir", tmpDir.getAbsolutePath());
        config.put("rythm.i18n.message.resolver.impl", I18nResourceResolver.class.getName());
        config.put(RythmConfigurationKey.RESOURCE_LOADER_IMPLS.getKey(), new SiriusResourceLoader());
        config.put(RythmConfigurationKey.CODEGEN_SOURCE_CODE_ENHANCER.getKey(), new SiriusSourceCodeEnhancer());
        Rythm.init(config);
        Rythm.engine().registerFastTag(new IncludeExtensions());
    }

    private static class IncludeExtensions extends JavaTagBase {

        @Override
        public String __getName() {
            return "includeExtensions";
        }

        @Override
        protected void call(__ParameterList params, __Body body) {
            for (String ext : sirius.web.templates.Content.getExtensions((String) params.get(0).value)) {
                p(__engine.render("view/" + ext));
            }
        }
    }

    @Override
    public void stopped() {
        // This is a dirrty hack as the Rythm Engine starts a thread pool which is not shutdown on engine
        // shutdown. As long as this bug is not fixed in rythm, we need to rely on this hack :-(
        // Date: 18.09.14 / Rythm 1.0
        try {
            Field checkerField = RythmEngine.class.getDeclaredField("nonExistsTemplatesChecker");
            checkerField.setAccessible(true);
            Object checker = checkerField.get(Rythm.engine());
            if (checker != null) {
                Field schedulerField = checker.getClass().getDeclaredField("scheduler");
                schedulerField.setAccessible(true);
                ((ThreadPoolExecutor) schedulerField.get(checker)).shutdown();
            }
        } catch (Throwable e) {
            LOG.WARN("Cannot halt ThreadPoolExecutor of NonExistsTemplatesChecker (Rythm): " + e.getMessage() + " (" + e
                    .getClass()
                    .getSimpleName() + ")");
        }

        // Shut down rest of rythm...
        Rythm.shutdown();
    }

    @Override
    public void awaitTermination() {
        // Not supported by rythm...
    }

    @Override
    public String getName() {
        return "Rythm-Engine";
    }

    private class SiriusResourceLoader extends ResourceLoaderBase {
        @Override
        public String getResourceLoaderRoot() {
            return "";
        }

        @Override
        public ITemplateResource load(String path) {
            if (path.contains("://")) {
                path = Strings.split(path, "://").getSecond();
            }
            return content.resolve(path).map(u -> new URLTemplateResource(u)).orElse(null);
        }
    }

    private class SiriusSourceCodeEnhancer implements ISourceCodeEnhancer {
        @Override
        public List<String> imports() {
            List<String> result = Lists.newArrayList();
            result.add("sirius.kernel.commons.Strings");
            result.add("sirius.kernel.nls.NLS");

            return result;
        }

        @Override
        public String sourceCode() {
            return "";
        }

        @Override
        public Map<String, ?> getRenderArgDescriptions() {
            final Map<String, Object> map = Maps.newTreeMap();
            map.put("ctx", CallContext.class);
            map.put("user", UserContext.class);
            map.put("prefix", String.class);
            map.put("product", String.class);
            map.put("year", int.class);
            map.put("detailedVersion", String.class);
            map.put("isDev", Boolean.class);
            map.put("call", WebContext.class);
            map.put("template", String.class);
            map.put("lang", String.class);
            map.put("dateFormat", String.class);
            map.put("timeFormat", String.class);
            for (RythmExtension ext : extensions) {
                ext.collectExtensionNames(entity -> map.put(entity.getFirst(), entity.getSecond()));
            }
            return map;
        }

        @Override
        public void setRenderArgs(final ITemplate template) {
            CallContext ctx = CallContext.getCurrent();
            String url = template.__getName();
            if (template instanceof URLTemplateResource) {
                url = ((URLTemplateResource) template.__getTemplateClass(true).templateResource).getUrl();
            }
            ctx.addToMDC("template", url);
            WebContext wc = ctx.get(WebContext.class);

            template.__setRenderArg("ctx", ctx);
            template.__setRenderArg("user", ctx.get(UserContext.class));
            template.__setRenderArg("prefix", wc.getContextPrefix());
            template.__setRenderArg("product", Product.getProduct().getName());
            template.__setRenderArg("year", LocalDate.now().getYear());
            template.__setRenderArg("detailedVersion", Product.getProduct().getDetails());
            template.__setRenderArg("isDev", Sirius.isDev());
            template.__setRenderArg("call", wc);
            template.__setRenderArg("template", url);
            template.__setRenderArg("lang", NLS.getCurrentLang());
            template.__setRenderArg("dateFormat", NLS.get("RythmConfig.jsDateFormat"));
            template.__setRenderArg("timeFormat", NLS.get("RythmConfig.jsTimeFormat"));
            for (RythmExtension ext : extensions) {
                ext.collectExtensionValues(entity -> template.__setRenderArg(entity.getFirst(), entity.getSecond()));
            }
        }
    }
}
