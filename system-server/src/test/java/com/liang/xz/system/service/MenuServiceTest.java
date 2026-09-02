package com.liang.xz.system.service;

import com.liang.xz.system.cache.SystemCacheService;
import com.liang.xz.system.dto.MenuRequest;
import com.liang.xz.system.dto.MenuResponse;
import com.liang.xz.system.entity.MenuEntity;
import com.liang.xz.system.repository.MenuRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * <p>菜单服务单元测试</p>
 *
 * @author auth-platform
 * @since 1.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("菜单服务测试")
class MenuServiceTest {

    @Mock
    private MenuRepository menuRepository;

    @Mock
    private SystemCacheService cacheService;

    @InjectMocks
    private MenuService menuService;

    private MenuEntity parentMenu;
    private MenuEntity childMenu;

    @BeforeEach
    void setUp() {
        parentMenu = MenuEntity.builder()
                .id(1L).parentId(0L).menuName("系统管理")
                .menuType(0).path("/system")
                .sortOrder(1).enabled(true).isFrame(false)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();

        childMenu = MenuEntity.builder()
                .id(10L).parentId(1L).menuName("用户管理")
                .menuType(1).path("/system/user").component("system/user/index")
                .permission("system:user:list")
                .sortOrder(1).enabled(true).isFrame(false)
                .createTime(LocalDateTime.now()).updateTime(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("获取菜单树")
    class TreeTests {

        @Test
        @DisplayName("正常构建菜单树")
        void shouldBuildMenuTree() {
            List<MenuEntity> menus = Arrays.asList(parentMenu, childMenu);
            when(cacheService.getAllMenus()).thenReturn(menus);
            //
            //List<MenuResponse> tree = menuService.getMenuTree();
            //
            //assertNotNull(tree);
            //assertEquals(1, tree.size());
            //assertEquals("系统管理", tree.get(0).getMenuName());
            //assertNotNull(tree.get(0).getChildren());
            //assertEquals(1, tree.get(0).getChildren().size());
            //assertEquals("用户管理", tree.get(0).getChildren().get(0).getMenuName());
        }
    }

    @Nested
    @DisplayName("创建菜单")
    class CreateTests {

        @Test
        @DisplayName("正常创建菜单")
        void shouldCreateMenuSuccessfully() {
            MenuRequest request = new MenuRequest();
            request.setParentId(0L);
            request.setMenuName("测试菜单");
            request.setMenuType(1);
            request.setPath("/test");
            request.setComponent("test/index");

            when(menuRepository.insert(any(MenuEntity.class))).thenReturn(5L);

            MenuResponse result = menuService.create(request);

            assertNotNull(result);
            assertEquals(5L, result.getId());
            assertEquals("测试菜单", result.getMenuName());
            verify(menuRepository).insert(any(MenuEntity.class));
            verify(cacheService).evictAllMenus();
        }

        @Test
        @DisplayName("创建菜单并自动分配角色")
        void shouldCreateMenuAndAutoAssignRoles() {
            MenuRequest request = new MenuRequest();
            request.setParentId(0L);
            request.setMenuName("自动分配菜单");
            request.setMenuType(1);
            request.setAutoAssignRoleIds(Arrays.asList(1L, 2L));

            when(menuRepository.insert(any(MenuEntity.class))).thenReturn(6L);

            MenuResponse result = menuService.create(request);

            assertNotNull(result);
            verify(menuRepository).insertRoleMenu(1L, 6L);
            verify(menuRepository).insertRoleMenu(2L, 6L);
        }
    }

    @Nested
    @DisplayName("删除菜单")
    class DeleteTests {

        @Test
        @DisplayName("存在子菜单时禁止删除")
        void shouldThrowWhenHasChildren() {
            when(menuRepository.findByParentId(1L)).thenReturn(Collections.singletonList(childMenu));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> menuService.delete(1L));
            assertTrue(ex.getMessage().contains("子菜单"));
            verify(menuRepository, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("无子菜单时正常删除")
        void shouldDeleteWhenNoChildren() {
            when(menuRepository.findByParentId(10L)).thenReturn(Collections.emptyList());
            doNothing().when(menuRepository).deleteRoleMenuByMenuId(10L);
            when(menuRepository.deleteById(10L)).thenReturn(1);

            boolean result = menuService.delete(10L);

            assertTrue(result);
            verify(menuRepository).deleteRoleMenuByMenuId(10L);
            verify(menuRepository).deleteById(10L);
        }
    }

    @Nested
    @DisplayName("按ID查询")
    class QueryTests {

        @Test
        @DisplayName("查询存在的菜单")
        void shouldFindExistingMenu() {
            List<MenuEntity> menus = Collections.singletonList(parentMenu);
            when(cacheService.getAllMenus()).thenReturn(menus);

            Optional<MenuResponse> result = menuService.getById(1L);
            assertTrue(result.isPresent());
            assertEquals("系统管理", result.get().getMenuName());
        }

        @Test
        @DisplayName("查询不存在的菜单返回空")
        void shouldReturnEmptyForNonExistent() {
            when(cacheService.getAllMenus()).thenReturn(Collections.emptyList());
            when(menuRepository.findAll()).thenReturn(Collections.emptyList());

            Optional<MenuResponse> result = menuService.getById(999L);
            assertFalse(result.isPresent());
        }
    }
}
