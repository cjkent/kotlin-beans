# kotlin-beans
This library provides integration between Kotlin data classes and [Joda Beans](https://www.joda.org/joda-beans/) serialization.

Using this library allows immutable Kotlin data classes to be serialized and deserialized by Joda Beans by implementing the interface `ImmutableData`.  No other code changes are required.

Note this is not intended to be a full replacement for Joda Beans code generation for classes that will be consumed from Java code. The Kotlin classes will implement the Joda `Bean` interface, but will not have builders or factory methods that generated Joda Beans do in Java.

The intended use case for this library is for Kotlin immutable data classes that will be consumed in Kotlin code, but must be serialized and deserialized using Joda Beans serialization. The features that Joda Beans adds to Java classes are provided by Kotlin as part of the language.
