package org.aoju.bus.core.convert.impl;

import org.aoju.bus.core.convert.Converter;
import org.aoju.bus.core.utils.CollUtils;
import org.aoju.bus.core.utils.TypeUtils;

import java.lang.reflect.Type;
import java.util.Collection;

/**
 * 各种集合类转换器
 *
 * @author aoju.org
 * @version 3.0.1
 * @group 839128
 * @since JDK 1.8
 */
public class CollectionConverter implements Converter<Collection<?>> {

    /**
     * 集合类型
     */
    private final Type collectionType;
    /**
     * 集合元素类型
     */
    private final Type elementType;

    /**
     * 构造，默认集合类型使用{@link Collection}
     */
    public CollectionConverter() {
        this(Collection.class);
    }

    /**
     * 构造
     *
     * @param collectionType 集合类型
     */
    public CollectionConverter(Type collectionType) {
        this(collectionType, TypeUtils.getTypeArgument(collectionType));
    }

    /**
     * 构造
     *
     * @param collectionType 集合类型
     */
    public CollectionConverter(Class<?> collectionType) {
        this(collectionType, TypeUtils.getTypeArgument(collectionType));
    }

    /**
     * 构造
     *
     * @param collectionType 集合类型
     * @param elementType    集合元素类型
     */
    public CollectionConverter(Type collectionType, Type elementType) {
        this.collectionType = collectionType;
        this.elementType = elementType;
    }

    @Override
    public Collection<?> convert(Object value, Collection<?> defaultValue) throws IllegalArgumentException {
        Collection<?> result = null;
        try {
            result = convertInternal(value);
        } catch (RuntimeException e) {
            return defaultValue;
        }
        return ((null == result) ? defaultValue : result);
    }

    /**
     * 内部转换
     *
     * @param value 值
     * @return 转换后的集合对象
     */
    protected Collection<?> convertInternal(Object value) {
        final Collection<Object> collection = CollUtils.create(TypeUtils.getClass(collectionType));
        Type eleType = this.elementType;
        return CollUtils.addAll(collection, value, eleType);
    }

}