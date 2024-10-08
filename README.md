<details>
<summary><h2>IMPORTANT LICENCE NOTICE</h2></summary>

By using this project in any form, you hereby give your "express assent" for the terms of the license of this
project (see [License](#license)), and acknowledge that I (the author of this project) have fulfilled my obligation
under the license to "make a reasonable effort under the circumstances to obtain the express assent of recipients to
the terms of this License".
</details>

# ZSON
A tiny JSON5 parsing library for Java 8, with a focus on simplicity and minimizing size.

## Usage
First, include the library in your project. You can do this by adding the following to your `build.gradle(.kts)`:
<details>
<summary>Kotlin</summary>

```kotlin
repositories {
    maven("https://maven.blamejared.com")
}

dependencies {
    implementation("dev.nolij:zson:[version]")
}
```
</details>
<details>
<summary>Groovy</summary>

```groovy
repositories {
    maven { url 'https://maven.blamejared.com' }
}

dependencies {
    implementation 'dev.nolij:zson:[version]'
}
```
</details>

Replace `[version]` with the version of the library you want to use.
You can find the latest version on the [releases page](https://github.com/Nolij/ZSON/releases).

If you wish to use an older version of Java, we provide 2 downgraded jars for Java 17 and 8.
You can use them by appending a classifier `downgraded-[java version]` to the dependency, for example:
`implementation("dev.nolij:zson:[version]:downgraded-8")`.

<details>
<summary>A note about Java 8</summary>

The Java 8 version actually uses Java 5 bytecode (classfile version 49), but because we use Java 8 features (namely NIO),
it still requires Java 8 to run. The reason for doing this is that Java 5 bytecode doesn't have stack maps, which significantly
reduces the size of the resulting jar.
</details>

Then, you can use the library like so:
```java
import dev.nolij.zson.Zson; // static helper/parsing methods, instantiate for a writer 
import dev.nolij.zson.ZsonValue; // represents a JSON value with a comment
import java.util.Map;

import static dev.nolij.zson.Zson.*;

public class ZsonExample {
	public static void main(String[] args) {
		// Parse a JSON string
		String json = "{\"key\": \"value\"}";
		Map<String, ZsonValue> zson = Zson.parseString(json);
		System.out.println(zson.get("key")); // value

		// Write a JSON string
		Zson writer = new Zson().withIndent("  ").withExpandArrays(false);
		Map<String, ZsonValue> map = object( // Zson.object()
			entry("key", "comment", 4),
			entry("arr", "look, arrays work too!", array(1, 2, 3)),
			entry("obj", "and objects!", object(
					entry("key", "value")
				)),
				entry("null", "comments can also\nbe multiple lines", null)
		);
		String jsonString = writer.stringify(map);
		System.out.println(jsonString);
	}
}

```

This prints out:
```json5
{
  // comment
  "key": 4,
  // look, arrays work too!
  "arr": [ 1, 2, 3 ],
  // and objects!
  "obj": {
    "key": "value"
  },
  // comments can also
  // be multiple lines
  "null": null
}
```

## Serializing objects
ZSON can serialize objects to JSON using reflection. Here's an example:
```java
import dev.nolij.zson.Zson;
import dev.nolij.zson.ZsonField;
import dev.nolij.zson.ZsonValue;

public class Example {
	@ZsonField(comment = "This is a comment")
	public String key = "value";
	
	@ZsonField(include = true)
	private int number = 4;
	
	@ZsonField(exclude = true)
	public String excluded = "this won't be included";
	
	public static void main(String[] args) {
		Example example = new Example();
		Map<String, ZsonValue> zson = Zson.obj2map(example);
		System.out.println(new Zson()
                .withQuoteKeys(false).stringify(zson));
	}
}
```

This prints out:
```json5
{
  // This is a comment
  key: "value",
  number: 4
}
```

Use the `comment` parameter of the `@ZsonField` annotation to add a comment to a field, and the `include` and `exclude` parameters to include or exclude fields from serialization.
By default, all public fields are included, and all private fields are excluded. If they are annotated with `@ZsonField(include = true)`, static fields will be serialized but not deserialized.

Also see the [tests](src/test/java/ZsonTest.java) for more examples.

## Building
To build the project, run `./gradlew build` on Unix or `gradlew.bat build` on Windows.

## License

This project is licensed under OSL-3.0. For more information, see [LICENSE](LICENSE).
