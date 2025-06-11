package com.cube.wechat.selfapp.app.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * @author AspireLife
 * @version JDK 1.8
 * @date 2024年12月06日 14:44
 */
public class XmlUtil {


    /**
     * 简单解析xml
     * @param in
     * @return
     */
    public static Map<String,Object> parseXML(InputStream in){
        Map<String,Object> map=new HashMap<>();
        try {
            SAXReader saxReader = new SAXReader();
            Document document = saxReader.read(in);
            Element root = document.getRootElement();
            Iterator iterator = root.elementIterator();
            while (iterator.hasNext()){

                Element element = (Element) iterator.next();
                map.put(element.getName(),element.getStringValue());

            }
        } catch (DocumentException e) {
            e.printStackTrace();
        }

        return map;
    }

}
