/*
 * The MIT License
 *
 * Copyright (c) 2015-2020 aoju.org All rights reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.aoju.bus.cache.serialize;

import com.alibaba.fastjson.JSON;
import org.aoju.bus.core.lang.Charset;

/**
 * @author Kimi Liu
 * @version 5.5.3
 * @since JDK 1.8+
 */
public class FastJsonSerializer extends AbstractSerializer {

    private Class<?> type;

    public FastJsonSerializer(Class<?> type) {
        this.type = type;
    }

    @Override
    protected byte[] doSerialize(Object obj) throws Throwable {
        String json = JSON.toJSONString(obj);
        return json.getBytes(Charset.DEFAULT_UTF_8);
    }

    @Override
    protected Object doDeserialize(byte[] bytes) throws Throwable {
        String json = new String(bytes, 0, bytes.length, Charset.DEFAULT_UTF_8);
        return JSON.parseObject(json, type);
    }

}
