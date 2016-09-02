package io.github.cjkent.kjb

import org.joda.beans.*
import org.joda.beans.impl.BasicProperty
import org.joda.beans.impl.BasicPropertyMap
import org.joda.beans.ser.DeserializerProvider
import org.joda.beans.ser.SerDeserializer
import org.joda.convert.StringConvert
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.*
import kotlin.reflect.jvm.javaType

//===================================================================================================================

interface ImmutableData : ImmutableBean {

    override fun <R : Any> property(propertyName: String): Property<R> =
            metaBean().metaProperty<R>(propertyName).createProperty(this)

    override fun propertyNames(): Set<String> = metaBean().metaPropertyMap().keys

    override fun metaBean(): MetaBean = KotlinMetaBean(this.javaClass.kotlin)
}

//===================================================================================================================

data class KotlinMetaBean(val beanClass: KClass<out ImmutableBean>) : MetaBean {

    val propertyMap : Map<String, KProperty<*>>
    val metaPropertyMap : Map<String, MetaProperty<*>>

    init {
        propertyMap = beanClass.memberProperties.associateBy { it.name }
        metaPropertyMap = beanClass.memberProperties.associateBy({ it.name }, { KotlinMetaProperty(it, this) })
    }

    override fun metaPropertyCount(): Int = propertyMap.size

    override fun createPropertyMap(bean: Bean): PropertyMap = BasicPropertyMap.of(bean)

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
}

//===================================================================================================================

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

//===================================================================================================================

data class KotlinBeanBuilder<T : ImmutableBean>(val metaBean: KotlinMetaBean) : BeanBuilder<T> {

    private val propertyValues: MutableMap<String, Any> = HashMap()

    override fun get(metaProperty: MetaProperty<*>): Any? = get(metaProperty.name())

    override fun get(propertyName: String): Any? = propertyValues[propertyName]

    override fun setAll(propertyValueMap: Map<String, Any>): BeanBuilder<T> {
        for ((propertyName, propertyValue) in propertyValueMap) set(propertyName, propertyValue)
        return this
    }

    override fun setString(propertyName: String, value: String): BeanBuilder<T> {
        val propertyType = metaBean.metaProperty<Any>(propertyName).propertyType()
        val convertedValue = StringConvert.INSTANCE.convertFromString(propertyType, value)
        return set(propertyName, convertedValue)
    }

    override fun setString(metaProperty: MetaProperty<*>, value: String): BeanBuilder<T> {
        val convertedValue = StringConvert.INSTANCE.convertFromString(metaProperty.propertyType(), value)
        return set(metaProperty, convertedValue)
    }

    override fun set(metaProperty: MetaProperty<*>, value: Any): BeanBuilder<T> = set(metaProperty.name(), value)

    override fun set(propertyName: String, value: Any): BeanBuilder<T> {
        propertyValues[propertyName] = value
        return this
    }

    @Suppress("UNCHECKED_CAST")
    override fun build(): T {
        val primaryConstructor = metaBean.beanClass.constructors.toList()[0]
        val constructorArgs = primaryConstructor.parameters.associateBy({ it }, { propertyValues[it.name] })
        return primaryConstructor.callBy(constructorArgs) as T
    }
}

//===================================================================================================================

object KotlinSerDeserializer : SerDeserializer {

    @Suppress("UNCHECKED_CAST")
    override fun findMetaBean(beanType: Class<*>): MetaBean = KotlinMetaBean(beanType.kotlin as KClass<out ImmutableBean>)

    override fun createBuilder(beanType: Class<*>, metaBean: MetaBean): BeanBuilder<*> =
            KotlinBeanBuilder<ImmutableBean>(metaBean as KotlinMetaBean)

    override fun findMetaProperty(beanType: Class<*>, metaBean: MetaBean, propertyName: String): MetaProperty<*> =
            metaBean.metaProperty<Any>(propertyName)

    override fun setValue(builder: BeanBuilder<*>, metaProp: MetaProperty<*>, value: Any) {
        builder.set(metaProp, value)
    }

    override fun build(beanType: Class<*>, builder: BeanBuilder<*>): Any = builder.build()
}

//===================================================================================================================

object KotlinDeserializerProvider : DeserializerProvider {

    override fun findDeserializer(type: Class<*>): SerDeserializer? =
            // TODO Introspect the class to check if it's an immutable Kotlin data class
            if (type.`package`.name == "io.github.cjkent.kjb") KotlinSerDeserializer else null
}
