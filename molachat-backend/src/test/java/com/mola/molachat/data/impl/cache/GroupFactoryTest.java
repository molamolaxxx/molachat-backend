package com.mola.molachat.data.impl.cache;

import com.mola.molachat.MolachatApplicationTests;
import com.mola.molachat.data.GroupFactoryInterface;
import com.mola.molachat.entity.Group;
import com.mola.molachat.service.impl.GroupServiceImpl;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.List;

public class GroupFactoryTest extends MolachatApplicationTests {

    /**
     * 需要模拟的bean，可能未开发完毕，涉及RPC调用或数据库调用
     */
    @MockBean
    private GroupFactoryInterface groupFactoryInterface;

    /**
     * 注入的bean，依赖需要模拟的bean
     */
//    @Resource
//    private GroupService groupService;
    /**
     * 直接mock，不需要注入容器，也就是不用写@Service注解
     */
    @Mock
    private GroupServiceImpl groupService;

    @Before
    public void before() {
        List<Group> list = new ArrayList<>();
        Group group = new Group();
        group.setName("mockito-test");
        list.add(group);
        Mockito.when(groupFactoryInterface.list()).thenReturn(list);
    }

    @Test
    public void test() {
//        for (Group group : groupFactoryInterface.list(e -> e.getId().contains("common"))) {
//            System.out.println(group);
//        }
    }


    @Test
    public void mockTest() {
        for (Group group : groupService.listAll()) {
            System.out.println(group);
        }
    }

}