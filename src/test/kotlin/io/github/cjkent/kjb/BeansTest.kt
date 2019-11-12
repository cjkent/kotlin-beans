package io.github.cjkent.kjb

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.ImmutableSortedSet
import io.github.cjkent.JodaBeanContainsFoo
import org.assertj.core.api.Assertions.assertThat
import org.joda.beans.Bean
import org.joda.beans.ser.JodaBeanSer
import org.testng.annotations.Test

data class Foo(val bar: Int, val baz: String) : ImmutableData, Comparable<Foo> {
    override fun compareTo(other: Foo): Int = bar.compareTo(other.bar)
}
data class ContainsFoo(val foo: Foo) : ImmutableData
data class ContainsListOfFoo(val foo: List<Foo>) : ImmutableData
data class ContainsImmutableListOfFoo(val foo: ImmutableList<Foo>) : ImmutableData
data class ContainsImmutableSetOfFoo(val foo: ImmutableSet<Foo>) : ImmutableData
data class ContainsImmutableSortedSetOfFoo(val foo: ImmutableSortedSet<Foo>) : ImmutableData
data class ContainsJodaBean(val jodaBean: JodaBeanContainsFoo) : ImmutableData
data class Bar(val baz: Double) : ImmutableData
data class ContainsMapOfBeans(val map: Map<Foo, Bar>) : ImmutableData
data class ContainsImmutableMapOfBeans(val map: ImmutableMap<Foo, Bar>) : ImmutableData

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

    fun containsImmutableListOfFoo() {
        val bean = ContainsImmutableListOfFoo(ImmutableList.of(Foo(42, "abc"), Foo(27, "xyz")))
        serializeDeserialize(bean)
    }

    fun containsImmutableSetOfFoo() {
        val bean = ContainsImmutableSetOfFoo(ImmutableSet.of(Foo(42, "abc"), Foo(27, "xyz")))
        serializeDeserialize(bean)
    }

    fun containsImmutableSortedSetOfFoo() {
        val bean = ContainsImmutableSortedSetOfFoo(ImmutableSortedSet.of(Foo(42, "abc"), Foo(27, "xyz")))
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

    fun containsImmutableMapOfBeans() {
        val foo1 = Foo(42, "abc")
        val foo2 = Foo(27, "xyz")
        val bar1 = Bar(123.4)
        val bar2 = Bar(432.1)
        val bean = ContainsImmutableMapOfBeans(ImmutableMap.of(foo1, bar1, foo2, bar2))
        serializeDeserialize(bean)
    }

    fun jodaBeanContainsFoo() {
        val bean = JodaBeanContainsFoo.builder().foo(Foo(42, "abc")).build()
        serializeDeserialize(bean)
    }

    private fun <T : Bean> serializeDeserialize(bean: T) {
        val json = JodaBeanSer.COMPACT.jsonWriter().write(bean)
        val deserialized = JodaBeanSer.COMPACT.jsonReader().read(json)
        assertThat(deserialized).isEqualTo(bean)
    }
}
