## Starting out

The translation files are at [/android/assets/jsons/translations](/jsons/translations)

If you're adding a new language, you'll need to create a new file ('Create a new file' to the right of the folder name in the UI), and copy into it the contents of template.properties

If you're adding stuff to an existing language, simply start editing the file!

You don't need to download anything, all translation work can be done on the Github website :)

When you feel that you're ready to add your translation to the game, you'll need to create a merge request, which takes your changes and puts them into the main version of the game - it's pretty straightforward once you do it

## Pitfalls

- If a translation template (the stuff to the left of "` = `") contains square brackets, you will have to include each of them _verbatim_ in your translation, but you can move them. Upper/lower case is relevant! e.g. `All [personFilter] are cool` can be translated as `Tous les [personFilter] sont cool`, but ***not*** as `Tous les [personnages] sont cool`, and neither as `Nous sommes vraiment cool`. Failing this is the main cause of your PR's showing up with red "x"es and "checks failed".

- Blanks: Watch out for blanks at the start of a line or two of them before the equals sign. If you got such a line - those blanks are part of the translation key and must not be deleted on the left side, and you should probably also include them in your translation (unless your language doesn't need spaces to separate things).

- Changes in the templates: When we find a typo in the english texts and fix it, or marginally change a wording, the template changes. Often the old template will not be automatically fixed in the existing translations, because it's a lot of work _and_ in most cases the developers cannot be sure the translation is still correct. For you, that might look like your translations are simply disappearing with an update. In such a case, you have the option to use github's history to look up old versions, copy the old translation, place it where the new template now says "requires translation" - and proofread and adapt it to the new english version. The history link for each file is in the top right area and has a nice round clock icon.

## Wait, what just happened?

Like most open-source projects, Unciv is developed at Github, so if you don't have a user you'll first have to create one. The way Github works is the following:

1. You create a 'fork' repo, i.e. copy, of Unciv that belongs to your user (myUser/Unciv)

2. You make changes to your copy. These changes are called a 'commit'.

3. You make a pull request, which is basically asking for the changes you made on myUser/Unciv to be merged into the main repo (yairm210/Unciv)

When you ask to 'edit' a file in yairm210/Unciv, these stages happen *automatically* - but it's important to understand what's happening behind the scenes do you understand where the changes actually are!

## Why not use a crowdsourcing translation website like <...>?

1. Testing. Currently, translations undergo a number of tests for verification. This allows some language changes to be accepted and others not, and it's all in the same platform with the same tests. External translation tools don't allow for this.

2. History and revisions. This is what Git was made for, and nothing like it exists in the world. I'm not exaggerating.

3. Release cycle. We release versions weekly. If we need to take information from an external website every time, and for many that I've checked - you need to download the info as a csv or something and convert it. Every extra step hurts.

4. Discussions. Most crowdsourcing translation websites don't allow for discussions and corrections on translations. Github does.

5. Mass changes. If we're changing the source of the translation but want to keep the various destinations (say, we change "Gold from trade routes +[amount]%" to "+[amount]% Gold from trade routes"), if all the translation files are in Git we can do that in 1 minute. If it's external, this varies greatly.

## Other notes

Make sure that you make the changes in the 'master' branch in your repo!

Each untranslated phrase will have a "requires translation" line before it, so you can quickly find them. You don't need to remove them yourself if you don't want to - they will be automatically removed the next time we rebuild the file.

Do as much as you're comfortable with - it's a big game with a lot of named objects, so don't feel pressured into doing everything =)

Note that Right-to-Left languages such as Arabic and Hebrew are not supported by the framework :/


# Translation generation - for developers

## The automatic template generation
Before releasing every version, we regenerate the translation files.

Sometimes, new strings (names, uniques, etc) are added in the json files. In order to not have to add every single one to the translation files manually, we have a class - TranslationFileWriter - that, for every language:

- Goes over the template.properties and copies translation lines
- For every json file in the jsons folder
    - Selects all string values - both in objects, and in arrays in objects
    - Generates a 'key = value' line

This means that every text that ISN'T in the jsons needs to be added manually to the template.properties in order to be translated!
That also means if you've been adding new json structures you (or someone) should check TranslationFileWriter and see if it is able to cope with them.

## Rules for templates added manually
Building a new UI and doing something like `popup.add("Hello world".toLabel())` is a typical case: This is not contained in json data, so you'll have to add the template to `template.properties` yourself. For this example, adding `Hello world = ` somewhere in a line of its own could suffice.

Note the space at the end - it's absolutely required, and see to it your editor does not destroy your work. If you want to make sure, use Android Studio for git integration, but edit the file in an external editor, then run the unit tests locally before pushing. (to do: add link for instructions how to do that)

Leading spaces on a translation line or more than one space between the text and the `=` would mean these spaces are a _key part of the string to be translated_. That can work, but be warned: translators often overlook that those spaces are a required part of _both_ template _and_ translation, so if you _can_ do without, then doing without is safer.

Translation templates can use placeholders, and there's two varieties: `[]` and `{}`. Square ones take precedence over curly ones, and nesting works only with a single level of curly nested inside one level of square. I both cases the symbols themselves (`[]{}`) are removed by the translation engine.

Square brackets `[]` mean the outer and inner components are both translated individually. The outer template will use alias names inside the brackets - example: Your code outputs "Everyone gains [5000] gold!", then the translation template should be "Everyone gains [amount] gold! = ". The translation engine would translate the "Everyone gains [] gold!" and "5000" individually and reassemble them - of course, the number is simply passed through. But in other cases that could be e.g. a Unit name that would be translated, and you could trust that translations for units are already handled just fine. Note that [uniques](../Modders/Unique-parameter-types.md) often use the feature, but it is in no way limited to them. It it makes life easier for translators, use it.

Curly brackets `{}` are simpler - the contents within the brackets are translated individually, while the outer parts are passed through verbatim. Example: `"+$amount${Fonts.gold} {Gold}".toLabel()` - note the first `${}` is a kotlin template while the second pair becomes part of the string. It tells the translation engine to ignore the numbers and the symbol but to translate the single word "Gold".
