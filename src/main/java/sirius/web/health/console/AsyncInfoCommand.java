/*
 * Made with all the love in the world
 * by scireum in Remshalden, Germany
 *
 * Copyright by scireum GmbH
 * http://www.scireum.de - info@scireum.de
 */

package sirius.web.health.console;

import sirius.kernel.async.Async;
import sirius.kernel.async.AsyncExecutor;
import sirius.kernel.di.std.Register;

import javax.annotation.Nonnull;

/**
 * Console command which reports statistics for all known executors.
 *
 * @author Andreas Haufler (aha@scireum.de)
 * @since 2013/08
 */
@Register
public class AsyncInfoCommand implements Command {

    @Override
    public void execute(Output output, String... params) throws Exception {
        output.apply("%-20s %8s %8s %8s %12s %8s %8s", "POOL", "ACTIVE", "QUEUED", "TOTAL", "DURATION", "BLOCKED", "DROPPED");
        output.separator();
        for (AsyncExecutor exec : Async.getExecutors()) {
            output.apply("%-20s %8d %8d %8d %12.1f %8d %8d",
                         exec.getCategory(),
                         exec.getActiveCount(),
                         exec.getQueue().size(),
                         exec.getExecuted(),
                         exec.getAverageDuration(),
                         exec.getBlocked(),
                         exec.getDropped());
        }
        output.separator();
    }

    @Override
    @Nonnull
    public String getName() {
        return "async";
    }

    @Override
    public String getDescription() {
        return "Reports the status of the task queueing system";
    }
}
