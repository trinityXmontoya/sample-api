- how do you distinguish between an invalid version and a version that simply has no vulnerabilities?
- i'm assuming that, as in the real word, package names are case-sensitive. i'm also assuming that, as is not always true
in the real world, that we trust the integrity of our licenses + vulnerabilities data.
- depending on how often licenses + vulnerabilities are updated, I would consider preseeding the application with a list
of current known packages. this way the call to search for health + releases could check a known enum rather than read
through the whole file. that being said, if this information was indexed in a database we wouldn't need to do any of that ;)