# Contributing to Diorite Isn't Useless

Thanks for wanting to help! Bug reports, ideas, code and translations are all welcome.
You can write in **English** or **Spanish**.

## Reporting bugs

Open an issue [here](https://github.com/Daio7771/DioriteIsntUseless/issues) and include:

1. Minecraft and mod version.
2. Other mods you have installed.
3. Your `latest.log` file (or the crash report if Minecraft crashes). You'll find them in the `logs` and `crash-reports` folders of your Minecraft directory.
4. Steps to reproduce the bug.

## Suggesting ideas

Open an [issue](https://github.com/Daio7771/DioriteIsntUseless/issues) and explain your idea. Good ideas:

- Give diorite (or the mod's items) a new use.
- Fit the mod's tone: useful tools, quiet horror.
- Follow the golden rules: no crashes, no flashing lights, nothing outside the game, everything reversible.

## Pull requests

Want to fix something or add a feature? Great. Just keep these in mind:

- **One change per PR.** Small PRs get reviewed faster.
- **Test it in game**, not just in the compiler. Tell me how you tested it.
- **Talk to me first** (in an issue) before adding new dependencies or big features.
- **Golden rules:** no crashes, no flashing lights, nothing outside the game (no internet, no files, no programs), and everything must be reversible.

To build and test:
```bash
./gradlew build      # the jar ends up in build/libs/
./gradlew runClient  # opens a test client
```

## Using AI

This mod was made with AI, so AI-assisted PRs are welcome too. But I'm stricter with them:

- Say it in the PR: which tool you used and for which parts.
- You must understand every line you send. If you can't explain it, I'll close the PR.

## Translations

Language files are in `src/main/resources/assets/dioriteisntuseless/lang/`. Copy `en_us.json`, translate it, and open a PR.

Some lines stay in English on purpose: names like The Miner and everything diorite "says". If you're not sure about a line, leave it in English and ask.

## License

By contributing, you agree that your work is shared under the [MIT License](https://github.com/Daio7771/DioriteIsntUseless/blob/main/LICENSE.txt).
