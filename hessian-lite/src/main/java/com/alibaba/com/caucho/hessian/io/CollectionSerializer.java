/*
 * Copyright (c) 2001-2004 Caucho Technology, Inc.  All rights reserved.
 *
 * The Apache Software License, Version 1.1
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Caucho Technology (http://www.caucho.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "Hessian", "Resin", and "Caucho" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    info@caucho.com.
 *
 * 5. Products derived from this software may not be called "Resin"
 *    nor may "Resin" appear in their names without prior written
 *    permission of Caucho Technology.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL CAUCHO TECHNOLOGY OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Scott Ferguson
 */

package com.alibaba.com.caucho.hessian.io;


import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Serializing a JDK 1.2 Collection.
 */
public class CollectionSerializer extends AbstractSerializer {
    //private Logger log = LoggerFactory.getLogger(this.getClass());

    private boolean _sendJavaType = true;

    /**
     * Set true if the java type of the collection should be sent.
     */
    public void setSendJavaType(boolean sendJavaType) {
        _sendJavaType = sendJavaType;
    }

    /**
     * Return true if the java type of the collection should be sent.
     */
    public boolean getSendJavaType() {
        return _sendJavaType;
    }

    public void writeObject(Object obj, AbstractHessianOutput out)
            throws IOException {
        if (out.addRef(obj))
            return;

        Collection list = (Collection) obj;

        Class cl = obj.getClass();
        boolean hasEnd;

        if (cl.equals(ArrayList.class)
                || !_sendJavaType
                || !Serializable.class.isAssignableFrom(cl))
            hasEnd = out.writeListBegin(list.size(), null);
        else
            hasEnd = out.writeListBegin(list.size(), obj.getClass().getName());

        /**
         * 修改序列化过程丢失属性的bug, 对继承自Collection并扩展了新属性的类，对其新增属性序列化。
         *
         * Added By HuQingmiao(443770574@qq.com) on 2017-03-25.
         */
        /** begin **/
        try {
            Class clasz = list.getClass();

            //记录已经写过的子类属性，以防被同名父类属性覆盖
            Set<String> fieldNameSet = new HashSet<String>();

            // 从当前自定义List子类逐层向上处理，对各层属性进行序列化
            for (; !clasz.getName().startsWith("java."); clasz = clasz.getSuperclass()) {

                // 如果当前类直接实现了List或Set接口，则不对其元素进行读写. 2017-08-28
                boolean impListOrSet = false;
                for (Class c : clasz.getInterfaces()) {
                    if (List.class.equals(c) | Set.class.equals(c) | SortedSet.class.equals(c) | Collection.class.equals(c)) {
                        impListOrSet = true;
                        break;
                    }
                }
                if (impListOrSet) {
                    continue;
                }

                // 如果当前类直接继承AbstractCollection/AbstractList/ABstractSet类，则不对其元素进行读写. 2017-08-29
                Class sc = clasz.getSuperclass();
                if (AbstractList.class.equals(sc) | AbstractSet.class.equals(sc) | AbstractCollection.class.equals(sc)) {
                    continue;
                }

                Field[] fields = clasz.getDeclaredFields();
                for (Field field : fields) {
                    //log.debug(">> " + clasz.getSimpleName() + "." + field.getName() + " " + field.getType());

                    // 子类属性已被写入，不再写入同名父属性
                    if (fieldNameSet.contains(field.getName())) {
                        continue;
                    }
                    if (Modifier.isTransient(field.getModifiers()) || Modifier.isStatic(field.getModifiers())) {
                        continue;
                    }
                    boolean isAccessible = field.isAccessible();
                    if (!isAccessible) {
                        field.setAccessible(true);
                    }

                    Object val = field.get(list);
                    //log.debug(">> "+clasz.getSimpleName()+" "+field.getName()+" "+field.getType()+" "+val);

                    out.writeObject(val);
                    field.setAccessible(isAccessible);

                    // 记录已写过的属性
                    fieldNameSet.add(field.getName());
                }
            }// end for (; !clasz.getName()

            fieldNameSet.clear();

        } catch (IllegalAccessException e) {
            throw new IOException(e.getMessage());
        }
        /** end **/

        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Object value = iter.next();

            out.writeObject(value);
        }

        if (hasEnd)
            out.writeListEnd();
    }
}
