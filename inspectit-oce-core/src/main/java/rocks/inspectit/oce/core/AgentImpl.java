package rocks.inspectit.oce.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import rocks.inspectit.oce.bootstrap.IAgent;
import rocks.inspectit.oce.core.config.InspectitEnvironment;
import rocks.inspectit.oce.core.config.spring.SpringConfiguration;
import rocks.inspectit.oce.core.logging.logback.LogbackInitializer;

import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.jar.JarFile;

/**
 * Implementation for the {@link IAgent} interface.
 * This class is responsible for setting up the spring context for inspectIT.
 *
 * @author Jonas Kunz
 */
public class AgentImpl implements IAgent {

    // statically initialize our default logging before doing anything
    static {
        LogbackInitializer.initDefaultLogging();
        LOGGER = LoggerFactory.getLogger(AgentImpl.class);
    }

    private static final String OPENCENSUS_FAR_JAR_PATH = "/opencensus-fat.jar";

    /**
     * Logger that is initialized in the static init block
     */
    private static final Logger LOGGER;

    /**
     * Created application context.
     */
    private AnnotationConfigApplicationContext ctx;

    @Override
    public void start(String cmdArgs, Instrumentation instrumentation) {
        ClassLoader classloader = AgentImpl.class.getClassLoader();

        LOGGER.info("Starting inspectIT OCE Agent...");

        ctx = new AnnotationConfigApplicationContext();
        ctx.setClassLoader(classloader);
        InspectitEnvironment environment = new InspectitEnvironment(ctx, Optional.ofNullable(cmdArgs));

        // once we have the environment, init the logging with the config
        LogbackInitializer.initLogging(environment.getCurrentConfig());

        try {
            boolean pushOCtoBootstrap = environment.getCurrentConfig().isPublishOpencensusToBootstrap();
            loadOpenCensus(pushOCtoBootstrap, instrumentation, classloader);
        } catch (Exception e) {
            LOGGER.error("Error loading opencensus classes, terminating agent", e);
            destroy();
            return;
        }

        ctx.registerShutdownHook();

        //Allows to use autowiring to acquire the Instrumentation instance
        ctx.addBeanFactoryPostProcessor(bf -> bf.registerSingleton("instrumentation", instrumentation));

        ctx.register(SpringConfiguration.class);
        ctx.refresh();
    }


    @Override
    public void destroy() {
        LOGGER.info("Shutting down inspectIT OCE Agent");
        ctx.close();
    }

    private void loadOpenCensus(boolean publishOpencensusToBootstrap, Instrumentation instr, ClassLoader classloader) throws Exception {
        Path jarFile = copyResourceToTempJarFile(OPENCENSUS_FAR_JAR_PATH);
        if (publishOpencensusToBootstrap) {
            LOGGER.info("Loading OpenCensus to the bootstrap classloader.");
            instr.appendToBootstrapClassLoaderSearch(new JarFile(jarFile.toFile()));
        } else {
            LOGGER.info("Loading OpenCensus in inspectIT classloader.");
            classloader.getClass()
                    .getMethod("addURL", URL.class)
                    .invoke(classloader, jarFile.toUri().toURL());
        }
    }

    /**
     * Copies the given resource to a new temporary file with the ending ".jar"
     *
     * @param resourcePath the path to the resource
     * @return the path to the generated jar file
     * @throws IOException
     */
    private static Path copyResourceToTempJarFile(String resourcePath) throws IOException {
        try (InputStream is = AgentImpl.class.getResourceAsStream(resourcePath)) {
            Path targetFile = Files.createTempFile("", ".jar");
            Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
            targetFile.toFile().deleteOnExit();
            return targetFile;
        }
    }
}
