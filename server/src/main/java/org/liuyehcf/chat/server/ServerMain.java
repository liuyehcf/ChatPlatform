package org.liuyehcf.chat.server;

import org.liuyehcf.chat.server.dao.CrmUserDAO;
import org.liuyehcf.chat.server.entity.CrmUser;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by Liuye on 2017/6/5.
 */
public class ServerMain {



    public static void main(String[] args) {
        ApplicationContext context = new ClassPathXmlApplicationContext("mybatis.xml");
        CrmUserDAO crmUserDAO = context.getBean("crmUserDAO", CrmUserDAO.class);
        CrmUser crmUser = crmUserDAO.selectCrmUserById("贺辰枫");

        System.out.println(crmUser);

        crmUser.setUserName("吕婳");
        crmUser.setUserPwd("lhhcf19920825");

        System.out.println(crmUserDAO.insertCrmUser(crmUser));
    }
}
