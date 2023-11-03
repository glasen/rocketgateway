package rocketgateway.config;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class CommandLineParser {

    private Namespace res;

    public CommandLineParser(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("RocketGateway")
                .build()
                .description("Small SMTP-Daemon which receives e-mails and sends them via RocketChat.");

        parser.addArgument("-c", "--configfile")
                .metavar("CONFIGFILE")
                .help("Name of config-file")
                .required(true)
                .type(String.class);


        try {
            this.res = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }
    }


    public Namespace getRes() {
        return res;
    }
}
