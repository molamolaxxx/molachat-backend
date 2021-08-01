package com.mola.molachat.service;

import com.mola.molachat.MolachatApplicationTests;
import com.mola.molachat.entity.Group;
import com.mola.molachat.entity.dto.ChatterDTO;
import com.mola.molachat.form.GroupForm;
import org.junit.Test;

import javax.annotation.Resource;

import static org.junit.Assert.*;

public class GroupServiceTest extends MolachatApplicationTests {

    @Resource
    private GroupService groupService;

    @Resource
    private ChatterService chatterService;

    @Test
    public void listAll() {
        for (Group group : groupService.listAll()) {
            System.out.println(group);
        }
    }

    @Test
    public void listByOwner() {
        for (ChatterDTO chatterDTO : chatterService.list()) {
            System.out.println(chatterDTO);
        }
    }

    @Test
    public void listByMemberId() {
    }

    @Test
    public void create() {
        GroupForm groupForm = new GroupForm();
        groupForm.setCreatorId("12345");
        groupForm.setDesc("哈哈哈哈");
        groupForm.setName("测试群组");
        groupForm.setCreatorId("1592119591617QJ4xJ");
        groupService.create(groupForm, true);
    }
}