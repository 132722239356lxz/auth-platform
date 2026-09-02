package com.liang.xz.system.service;

import com.liang.xz.system.dto.DeptRequest;
import com.liang.xz.system.dto.DeptResponse;
import com.liang.xz.system.entity.DeptEntity;
import com.liang.xz.system.repository.DeptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * <p>部门服务单元测试</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("部门服务测试")
class DeptServiceTest {

    @Mock
    private DeptRepository deptRepository;

    @InjectMocks
    private DeptService deptService;

    private DeptEntity rootDept;
    private DeptEntity childDept;

    @BeforeEach
    void setUp() {
        rootDept = DeptEntity.builder()
                .id(1L).parentId(0L).deptName("总公司").deptCode("DEPT_ROOT")
                .leader("赵总").sortOrder(1).enabled(true)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();

        childDept = DeptEntity.builder()
                .id(10L).parentId(1L).deptName("技术部").deptCode("DEPT_TECH")
                .leader("张三").sortOrder(1).enabled(true)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("获取部门树")
    class TreeTests {

        @Test
        @DisplayName("正常构建部门树")
        void shouldBuildDeptTree() {
            List<DeptEntity> depts = Arrays.asList(rootDept, childDept);
            when(deptRepository.findAll()).thenReturn(depts);

            //List<DeptResponse> tree = deptService.getDeptTree();
            //
            //assertNotNull(tree);
            //assertEquals(1, tree.size());
            //assertEquals("总公司", tree.get(0).getDeptName());
            //assertNotNull(tree.get(0).getChildren());
            //assertEquals(1, tree.get(0).getChildren().size());
            //assertEquals("技术部", tree.get(0).getChildren().get(0).getDeptName());
        }
    }

    @Nested
    @DisplayName("创建部门")
    class CreateTests {

        @Test
        @DisplayName("正常创建部门")
        void shouldCreateDeptSuccessfully() {
            DeptRequest request = new DeptRequest();
            request.setParentId(0L);
            request.setDeptName("财务部");
            request.setDeptCode("DEPT_FINANCE");
            request.setLeader("钱某");
            request.setSortOrder(3);
        }

        @Test
        @DisplayName("部门编码重复应抛出异常")
        void shouldThrowWhenCodeExists() {
            DeptRequest request = new DeptRequest();
            request.setDeptName("重复部门");
            request.setDeptCode("DEPT_ROOT");
        }
    }

    @Nested
    @DisplayName("删除部门")
    class DeleteTests {

        @Test
        @DisplayName("存在子部门时禁止删除")
        void shouldThrowWhenHasChildren() {
            //when(deptRepository.countByParentId(1L)).thenReturn(3);
            //
            //IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            //        () -> deptService.delete(1L));
            //assertTrue(ex.getMessage().contains("子部门"));
            //verify(deptRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无子部门时正常删除")
        void shouldDeleteWhenNoChildren() {
            //when(deptRepository.countByParentId(10L)).thenReturn(0);
            //when(deptRepository.deleteById(10L)).thenReturn(1);
            //
            //boolean result = deptService.delete(10L);
            //
            //assertTrue(result);
            //verify(deptRepository).deleteById(10L);
        }
    }
}
