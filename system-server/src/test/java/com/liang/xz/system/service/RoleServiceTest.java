package com.liang.xz.system.service;

import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.RoleRequest;
import com.liang.xz.system.dto.RoleResponse;
import com.liang.xz.system.entity.RoleEntity;
import com.liang.xz.system.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * <p>角色服务单元测试</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务测试")
class RoleServiceTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private SystemCacheService cacheService;

    @InjectMocks
    private RoleService roleService;

    private RoleEntity mockRole;

    @BeforeEach
    void setUp() {
        mockRole = RoleEntity.builder()
                .id(1L)
                .roleCode("ROLE_ADMIN")
                .roleName("系统管理员")
                .description("测试角色")
                .sortOrder(1)
                .enabled(true)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("创建角色")
    class CreateTests {

        @Test
        @DisplayName("正常创建角色")
        void shouldCreateRoleSuccessfully() {
            RoleRequest request = new RoleRequest();
            request.setRoleCode("ROLE_TEST");
            request.setRoleName("测试角色");
            request.setDescription("测试");
            request.setSortOrder(1);
            request.setMenuIds(null);

            when(roleRepository.findByRoleCode("ROLE_TEST")).thenReturn(Optional.empty());
            when(roleRepository.insert(any(RoleEntity.class))).thenReturn(1L);

            RoleResponse result = roleService.create(request);

            assertNotNull(result);
            assertEquals("ROLE_TEST", result.getRoleCode());
            assertEquals("测试角色", result.getRoleName());
            verify(roleRepository).insert(any(RoleEntity.class));
            verify(cacheService).evictAllRoles();
        }

        @Test
        @DisplayName("角色编码已存在应抛出异常")
        void shouldThrowWhenRoleCodeExists() {
            RoleRequest request = new RoleRequest();
            request.setRoleCode("ROLE_ADMIN");
            request.setRoleName("管理员");

            when(roleRepository.findByRoleCode("ROLE_ADMIN")).thenReturn(Optional.of(mockRole));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> roleService.create(request));
            assertTrue(ex.getMessage().contains("已存在"));
            verify(roleRepository, never()).insert(any());
        }
    }

    @Nested
    @DisplayName("更新角色")
    class UpdateTests {

        @Test
        @DisplayName("正常更新角色")
        void shouldUpdateRoleSuccessfully() {
            RoleRequest request = new RoleRequest();
            request.setRoleCode("ROLE_ADMIN_NEW");
            request.setRoleName("新管理员");
            request.setDescription("更新描述");
            request.setSortOrder(2);

            when(roleRepository.findById(1L)).thenReturn(Optional.of(mockRole));
            when(roleRepository.update(any(RoleEntity.class))).thenReturn(1);
            when(roleRepository.findMenuIdsByRoleId(anyLong())).thenReturn(Collections.emptyList());
            when(roleRepository.findUserIdsByRoleId(anyLong())).thenReturn(Collections.emptyList());

            Optional<RoleResponse> result = roleService.update(1L, request);

            assertTrue(result.isPresent());
            assertEquals("ROLE_ADMIN_NEW", result.get().getRoleCode());
        }

        @Test
        @DisplayName("更新不存在的角色返回空")
        void shouldReturnEmptyForNonExistentRole() {
            when(roleRepository.findById(999L)).thenReturn(Optional.empty());

            RoleRequest request = new RoleRequest();
            request.setRoleCode("X");
            request.setRoleName("X");

            Optional<RoleResponse> result = roleService.update(999L, request);
            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("删除角色")
    class DeleteTests {

        @Test
        @DisplayName("角色有关联用户时禁止删除")
        void shouldThrowWhenRoleHasUsers() {
            when(roleRepository.findUserIdsByRoleId(1L)).thenReturn(List.of(1L, 2L));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> roleService.delete(1L));
            assertTrue(ex.getMessage().contains("关联用户"));
            verify(roleRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无关联用户时正常删除")
        void shouldDeleteWhenNoUsers() {
            when(roleRepository.findUserIdsByRoleId(1L)).thenReturn(Collections.emptyList());
            doNothing().when(roleRepository).deleteRoleMenus(1L);
            when(roleRepository.deleteById(1L)).thenReturn(1);

            boolean result = roleService.delete(1L);

            assertTrue(result);
            verify(roleRepository).deleteRoleMenus(1L);
            verify(roleRepository).deleteById(1L);
        }
    }

    @Nested
    @DisplayName("启用/禁用角色")
    class StatusTests {

        @Test
        @DisplayName("启用角色")
        void shouldEnableRole() {
            when(roleRepository.updateEnabled(1L, true)).thenReturn(1);

            boolean result = roleService.enable(1L);
            assertTrue(result);
            verify(cacheService).evictRoleCache(1L);
        }

        @Test
        @DisplayName("禁用角色")
        void shouldDisableRole() {
            when(roleRepository.updateEnabled(1L, false)).thenReturn(1);
            when(roleRepository.findUserIdsByRoleId(1L)).thenReturn(Collections.emptyList());

            boolean result = roleService.disable(1L);
            assertTrue(result);
            verify(cacheService).evictRoleCache(1L);
        }
    }
}
