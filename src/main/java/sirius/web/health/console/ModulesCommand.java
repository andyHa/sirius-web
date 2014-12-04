/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.health.console;

import sirius.kernel.di.std.Register;
import sirius.kernel.info.Product;

import javax.annotation.Nonnull;

/**
 * Console command which reports all known modules ({@link sirius.kernel.info.Module}).
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2014/12
 */
@Register
public class ModulesCommand implements Command {

    @Override
    public void execute(Output output, String... params) throws Exception {
        output.line(Product.getProduct().toString());
        output.blankLine();
        output.line("MODULES");
        output.separator();
        Product.getModules().stream().forEach(m -> output.line(m.toString()));
        output.separator();
    }

    @Override
    @Nonnull
    public String getName() {
        return "modules";
    }

    @Override
    public String getDescription() {
        return "Reports version of the product and all installed modules.";
    }
}
