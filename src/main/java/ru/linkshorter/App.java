package ru.linkshorter;

public final class App {

    public static void main(String[] args) {
        var cli = ApplicationLoader.loadCli();
        if (args.length > 0) {
            System.exit(cli.execute(args));
        }
        cli.run();
    }
}
