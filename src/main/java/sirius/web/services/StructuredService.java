/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.services;

import sirius.kernel.xml.StructuredOutput;

/**
 * Provides a service which can be called via the HTTP interface and generate a structured output encoded as JSON or XML
 * <p>
 * A <tt>StructuredService</tt> must be registered using the {@link sirius.kernel.di.std.Register} annotation
 * provided with a name, which also defines the URL of the service.
 * <p>
 * The generated output can be either JSON or XML, which is completely handled by the framework.
 * <p>
 * Consider providing an {@link AutoDoc} annotation in order to provide a public API documentation for all services
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/11
 */
public interface StructuredService {

    /**
     * Handles the incoming call while using <tt>out</tt> to generate the result.
     *
     * @param call the HTTP request to process
     * @param out  the encoder used to generate the desired output
     * @throws Exception in case of an error. An appropriate result will be generated in the selected format.
     */
    void call(ServiceCall call, StructuredOutput out) throws Exception;

}
