package io.github.cjkent.kjb

import org.joda.beans.Bean
import org.joda.beans.BeanBuilder
import org.joda.beans.ImmutableBean
import org.joda.beans.MetaBean
import org.joda.beans.MetaProperty
import org.joda.beans.Property
import org.joda.beans.PropertyStyle
import org.joda.beans.impl.BasicProperty
import org.joda.beans.ser.DeserializerProvider
import org.joda.beans.ser.SerDeserializer
import org.joda.beans.ser.SerDeserializerProvider
import org.joda.convert.StringConvert
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.HashMap
import java.util.NoSuchElementException
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType

/**
 * Provides implementations of all [ImmutableBean] methods for Kotlin classes.
 *
 * A Kotlin class can become a Joda bean by implementing this interface. No function implementations are
 * needed as they are all provided by this interface.
 */
@DeserializerProvider(KotlinDeserializerProvider::class)
interface ImmutableData : ImmutableBean {

    override fun <R : Any> property(propertyName: String): Property<R> =
        metaBean().metaProperty<R>(propertyName).createProperty(this)

    override fun propertyNames(): Set<String> = metaBean().metaPropertyMap().keys

    override fun metaBean(): MetaBean = KotlinMetaBean.forType(this.javaClass.kotlin)
}

/**
 * [MetaBean] implementation for Kotlin classes that uses reflection.
 */
data class KotlinMetaBean(val beanClass: KClass<out ImmutableBean>) : MetaBean {

    private val propertyMap : Map<String, KProperty1<*, *>>
    private val metaPropertyMap : Map<String, MetaProperty<*>>

    init {
        propertyMap = beanClass.memberProperties.associateBy { it.name }
        metaPropertyMap = beanClass.memberProperties.associateBy({ it.name }, { KotlinMetaProperty(it, this) })
    }

    override fun isBuildable(): Boolean = true

    override fun metaPropertyCount(): Int = propertyMap.size

    override fun beanType(): Class<out Bean> = beanClass.java

    override fun metaPropertyIterable(): Iterable<MetaProperty<*>> = metaPropertyMap.values

    override fun metaPropertyExists(propertyName: String): Boolean = propertyMap.containsKey(propertyName)

    override fun metaPropertyMap(): Map<String, MetaProperty<*>> = metaPropertyMap

    @Suppress("UNCHECKED_CAST")
    override fun <R : Any> metaProperty(propertyName: String): MetaProperty<R> {
        val property = metaPropertyMap[propertyName] ?: throw NoSuchElementException("No property found named $propertyName")
        return property as MetaProperty<R>
    }

    override fun beanName(): String = beanClass.java.name

    override fun builder(): BeanBuilder<out Bean> = KotlinBeanBuilder(this)

    companion object {

        private val metaBeanCache: MutableMap<KClass<*>, KotlinMetaBean> = ConcurrentHashMap()

        fun <T : ImmutableBean> forType(type: KClass<T>): KotlinMetaBean {
            val cachedBean = metaBeanCache[type]
            if (cachedBean != null) return cachedBean
            val metaBean = KotlinMetaBean(type)
            metaBeanCache[type] = metaBean
            return metaBean
        }
    }
}

/**
 * [MetaProperty] implementation for Kotlin properties that uses reflection.
 */
@Suppress("UNCHECKED_CAST")
data class KotlinMetaProperty<T>(val property: KProperty<T>, val metaBean: KotlinMetaBean) : MetaProperty<T> {

    override fun metaBean(): MetaBean = metaBean

    override fun get(bean: Bean): T = property.getter.call(bean)

    override fun getString(bean: Bean): String = StringConvert.INSTANCE.convertToString(get(bean))

    override fun getString(bean: Bean, stringConvert: StringConvert): String = stringConvert.convertToString(get(bean))

    override fun annotations(): List<Annotation> = property.annotations

    override fun propertyGenericType(): Type = property.returnType.javaType

    override fun declaringType(): Class<*> = metaBean.beanType()

    override fun createProperty(bean: Bean): Property<T> = BasicProperty.of(bean, this)

    override fun <A : Annotation> annotation(annotation: Class<A>): A =
        annotations().find { it.javaClass == annotation } as A? ?:
        throw NoSuchElementException("No annotation found of type ${annotation.name}")

    override fun style(): PropertyStyle = PropertyStyle.IMMUTABLE

    override fun name(): String = property.name

    override fun propertyType(): Class<T> = property.returnType.javaType.let { type ->
        when (type) {
            is Class<*> -> type as Class<T>
            is ParameterizedType -> type.rawType as Class<T>
            else -> throw IllegalArgumentException("Unexpected type for return type ${property.returnType} of $property")
        }
    }

    // Unsupported mutator methods -------------------------------------------------

    override fun set(bean: Bean, value: Any) {
        throw UnsupportedOperationException("set not supported for immutable beans")
    }

    override fun put(bean: Bean, value: Any): T {
        throw UnsupportedOperationException("put not supported for immutable beans")
    }

    override fun setString(bean: Bean, value: String) {
        throw UnsupportedOperationException("setString supported for immutable beans")
    }

    override fun setString(bean: Bean, value: String, stringConvert: StringConvert) {
        throw UnsupportedOperationException("setString supported for immutable beans")
    }
}

/**
 * [BeanBuilder] implementation for Kotlin classes.
 */
@Suppress("UNCHECKED_CAST")
data class KotlinBeanBuilder<T : ImmutableBean>(val metaBean: KotlinMetaBean) : BeanBuilder<T> {

    private val propertyValues: MutableMap<String, Any> = HashMap()

    override fun <P> get(metaProperty: MetaProperty<P>): P? = get(metaProperty.name()) as P?

    override fun get(propertyName: String): Any? = propertyValues[propertyName]

    override fun set(metaProperty: MetaProperty<*>, value: Any): BeanBuilder<T> = set(metaProperty.name(), value)

    override fun set(propertyName: String, value: Any): BeanBuilder<T> {
        propertyValues[propertyName] = value
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun build(): T {
        val primaryConstructor = metaBean.beanClass.constructors.toList()[0]
        // Filter out the constructor parameters if there is no corresponding argument.
        // Construction will fail if any of these parameters are non-optional (don't have default values)
        // TODO check the exception thrown is understandable
        val constructorArgs = primaryConstructor.parameters
            .filter { propertyValues.contains(it.name) }
            .associateWith { propertyValues[it.name] }
        return primaryConstructor.callBy(constructorArgs) as T
    }
}

/**
 * [SerDeserializer] implementation for Kotlin classes.
 */
object KotlinSerDeserializer : SerDeserializer {

    @Suppress("UNCHECKED_CAST")
    override fun findMetaBean(beanType: Class<*>): MetaBean =
        KotlinMetaBean.forType(beanType.kotlin as KClass<out ImmutableBean>)

    override fun createBuilder(beanType: Class<*>, metaBean: MetaBean): BeanBuilder<*> =
        KotlinBeanBuilder<ImmutableBean>(metaBean as KotlinMetaBean)

    override fun findMetaProperty(beanType: Class<*>, metaBean: MetaBean, propertyName: String): MetaProperty<*> =
        metaBean.metaProperty<Any>(propertyName)

    override fun setValue(builder: BeanBuilder<*>, metaProp: MetaProperty<*>, value: Any) {
        builder.set(metaProp, value)
    }

    override fun build(beanType: Class<*>, builder: BeanBuilder<*>): Any = builder.build()
}

/**
 * [SerDeserializerProvider] that adds support for Kotlin to the Joda Beans serialization mechanism.
 */
class KotlinDeserializerProvider : SerDeserializerProvider {

    override fun findDeserializer(type: Class<*>): SerDeserializer? =
        if (ImmutableData::class.java.isAssignableFrom(type)) KotlinSerDeserializer else null
}
