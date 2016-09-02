package io.github.cjkent.kjb

import io.github.cjkent.JodaBeanContainsFoo
import org.assertj.core.api.Assertions.assertThat
import org.joda.beans.Bean
import org.joda.beans.ser.JodaBeanSer
import org.joda.beans.ser.SerDeserializers
import org.joda.beans.ser.json.JodaBeanJsonReader
import org.joda.beans.ser.json.JodaBeanJsonWriter
import org.testng.annotations.Test

data class Foo(val bar: Int, val baz: String) : ImmutableData
data class ContainsFoo(val foo: Foo) : ImmutableData
data class ContainsListOfFoo(val foo: List<Foo>) : ImmutableData
data class ContainsJodaBean(val jodaBean: JodaBeanContainsFoo) : ImmutableData
data class Bar(val baz: Double) : ImmutableData
data class ContainsMapOfBeans(val map: Map<Foo, Bar>) : ImmutableData

@Test
class MetaBeanTest {

    fun metaProperties() {
        val metaBean = KotlinMetaBean(Foo::class)
        val foo = Foo(42, "abc")
        assertThat(metaBean.metaProperty<Any>("bar").get(foo)).isEqualTo(42)
        assertThat(metaBean.metaProperty<Any>("baz").get(foo)).isEqualTo("abc")
    }
}

@Test
class SerializationTest {

    fun foo() {
        val bean = Foo(42, "abc")
        serializeDeserialize(bean)
    }

    fun containsFoo() {
        val bean = ContainsFoo(Foo(42, "abc"))
        serializeDeserialize(bean)
    }

    fun containsListOfFoo() {
        val bean = ContainsListOfFoo(listOf(Foo(42, "abc"), Foo(27, "xyz")))
        serializeDeserialize(bean)
    }

    fun containsJodaBean() {
        val bean = ContainsJodaBean(JodaBeanContainsFoo.builder().foo(Foo(42, "abc")).build())
        serializeDeserialize(bean)
    }

    fun containsMapOfBeans() {
        val foo1 = Foo(42, "abc")
        val foo2 = Foo(27, "xyz")
        val bar1 = Bar(123.4)
        val bar2 = Bar(432.1)
        val bean = ContainsMapOfBeans(mapOf(foo1 to bar1, foo2 to bar2))
        serializeDeserialize(bean)
    }

    fun jodaBeanContainsFoo() {
        val bean = JodaBeanContainsFoo.builder().foo(Foo(42, "abc")).build()
        val serDeserializers = SerDeserializers(KotlinDeserializerProvider)
        val settings = JodaBeanSer.COMPACT.withDeserializers(serDeserializers)
        val writer = JodaBeanJsonWriter(settings)
        val json = writer.write(bean)
        val reader = JodaBeanJsonReader(settings)
        val deserialized = reader.read(json)
        assertThat(deserialized).isEqualTo(bean)
    }
}

private fun <T : Bean> serializeDeserialize(bean: T) {
    val serDeserializers = SerDeserializers(KotlinDeserializerProvider)
    val settings = JodaBeanSer.COMPACT.withDeserializers(serDeserializers)
    val writer = JodaBeanJsonWriter(settings)
    val json = writer.write(bean)
    val reader = JodaBeanJsonReader(settings)
    val deserialized = reader.read(json)
    assertThat(deserialized).isEqualTo(bean)
}
