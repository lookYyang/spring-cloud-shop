package quick.pager.shop.manage.service.system.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import quick.pager.shop.constants.ResponseStatus;
import quick.pager.shop.manage.helper.MenuHelper;
import quick.pager.shop.manage.mapper.MenuMapper;
import quick.pager.shop.manage.mapper.RoleMapper;
import quick.pager.shop.manage.model.Menu;
import quick.pager.shop.manage.model.Role;
import quick.pager.shop.manage.param.system.SysUserPageParam;
import quick.pager.shop.manage.response.system.MenuResponse;
import quick.pager.shop.manage.response.system.SysUserResponse;
import quick.pager.shop.manage.service.system.SysUserService;
import quick.pager.shop.manage.utils.PrincipalUtils;
import quick.pager.shop.response.Response;
import quick.pager.shop.manage.param.system.SysUserParam;
import quick.pager.shop.manage.mapper.SysRoleMapper;
import quick.pager.shop.manage.mapper.SysUserMapper;
import quick.pager.shop.manage.model.SysRole;
import quick.pager.shop.manage.model.SysUser;
import quick.pager.shop.utils.BeanCopier;
import quick.pager.shop.utils.DateUtils;

/**
 * 系统用户服务
 *
 * @author siguiyang
 */
@Service
@Slf4j
public class SysUserServiceImpl implements SysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;
    @Autowired
    private SysRoleMapper sysRoleMapper;
    @Autowired
    private RoleMapper roleMapper;
    @Autowired
    private MenuMapper menuMapper;
    @Autowired
    private MenuHelper menuHelper;

    @Override
    public Response<List<SysUserResponse>> queryPage(SysUserPageParam param) {

        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (!StringUtils.isEmpty(param.getPhone())) {
            wrapper.eq(SysUser::getPhone, param.getPhone());
        }

        int total = sysUserMapper.selectCount(wrapper);
        List<SysUserResponse> responseList = Collections.emptyList();
        if (total > 0) {

            List<SysUser> list = sysUserMapper.selectPage(new Page<>(param.getPage(), param.getPageSize(), false), wrapper).getRecords();
            responseList = Optional.ofNullable(list).orElse(Collections.emptyList()).stream()
                    .map(this::convert).collect(Collectors.toList());
        }

        return Response.toResponse(responseList, total);
    }

    @Override
    public Response<Long> create(SysUserParam param) {

        SysUser sysUser = new SysUser();
        BeanCopier.copy(param, sysUser);

        sysUser.setPassword(new BCryptPasswordEncoder().encode(sysUser.getPassword()));
        sysUser.setCreateTime(DateUtils.dateTime());
        sysUser.setDeleteStatus(Boolean.FALSE);
        sysUserMapper.insert(sysUser);
        modifySysUser(param.getRoleIds(), sysUser);
        return new Response<>(sysUser.getId());
    }

    @Override
    public Response<Long> modify(SysUserParam param) {
        SysUser sysUser = new SysUser();
        BeanCopier.copy(param, sysUser);
        sysUserMapper.updateById(sysUser);
        modifySysUser(param.getRoleIds(), sysUser);
        return new Response<>(sysUser.getId());
    }

    @Override
    public Response adminInfo(String phone) {

        SysUser queryUser = new SysUser();
        queryUser.setPhone(phone);
        queryUser.setDeleteStatus(Boolean.FALSE);
        SysUser sysUser = sysUserMapper.selectOne(new QueryWrapper<>(queryUser));

        if (Objects.isNull(sysUser)) {
            return new Response<>(ResponseStatus.Code.FAIL_CODE, ResponseStatus.USER_PHONE_NOT_EXISTS);
        }

        SysUserResponse sysUserResponse = new SysUserResponse();
        // 登陆用户所有角色
        Collection<? extends GrantedAuthority> authorities = PrincipalUtils.getPrincipal().getAuthorities();

        List<String> permissions = authorities.stream().map(GrantedAuthority::getAuthority).distinct().collect(Collectors.toList());

        // 所有访问菜单的路由
        List<Menu> menus = menuHelper.selectMenuBySysUserId(sysUser.getId());
        // 父级菜单
        List<MenuResponse> topMenu = Optional.ofNullable(menus).orElse(Collections.emptyList()).stream()
                .filter(menu -> Objects.isNull(menu.getParentId()))
                .map(this::convert)
                .collect(Collectors.toList());
        // 这种写法是查出所有的菜单，移除顶级菜单，剩余的菜单就不是顶级菜单也就是parentId 都是不为null
        Map<Long, List<MenuResponse>> childrenMap = Optional.ofNullable(menus).orElse(Collections.emptyList()).stream()
                .filter(item -> Objects.nonNull(item.getParentId()))
                .map(this::convert)
                .collect(Collectors.groupingBy(MenuResponse::getParentId, Collectors.toList()));
        // 整合成父子结构
        toTree(topMenu, childrenMap);
        sysUserResponse.setPhone(sysUser.getPhone());
        sysUserResponse.setUsername(sysUser.getUsername());
        sysUserResponse.setRouters(topMenu);
        sysUserResponse.setPermissions(permissions);

        return new Response<>(sysUserResponse);
    }

    /**
     * 新增|修改
     */
    private void modifySysUser(List<Long> roleIds, SysUser sysUser) {

        roleIds.forEach(id -> {
            // 查询系统用户与角色关系
            LambdaQueryWrapper<SysRole> sysRoleWrapper = new LambdaQueryWrapper<>();
            sysRoleWrapper.eq(SysRole::getDeleteStatus, Boolean.FALSE);
            sysRoleWrapper.eq(SysRole::getSysUserId, sysUser.getId());
            List<SysRole> sysRoles = sysRoleMapper.selectList(sysRoleWrapper);

            LocalDateTime time = DateUtils.dateTime();
            sysRoles.forEach(item -> {
                SysRole sysRole = new SysRole();
                sysRole.setRoleId(id);
                sysRole.setSysUserId(sysUser.getId());
                sysRole.setCreateTime(time);
                sysRole.setUpdateTime(time);
                sysRole.setDeleteStatus(Boolean.FALSE);
                sysRoleMapper.insert(sysRole);
            });
        });
    }

    private MenuResponse convert(Menu menu) {
        MenuResponse resp = new MenuResponse();
        BeanCopier.copy(menu, resp);
        resp.setLabel(menu.getName());
        if (Objects.isNull(menu.getParentId())) {
            resp.setComponent("Layout");
        }
        return resp;
    }

    /**
     * 获取访问菜单
     *
     * @param menus       TOP菜单
     * @param childrenMap 孩子节点菜单
     */
    private void toTree(List<MenuResponse> menus, Map<Long, List<MenuResponse>> childrenMap) {
        menus.forEach(k -> {
            MenuResponse.Meta meta = new MenuResponse.Meta(k.getName(), k.getIcon(), k.getHidden(), Lists.newArrayList(k.getPermission()));
            k.setMeta(meta);
            List<MenuResponse> menuList = Optional.ofNullable(childrenMap.get(k.getId())).orElse(Collections.emptyList()).stream()
                    .filter(MenuResponse::getRouter)
                    .collect(Collectors.toList());
            toTree(menuList, childrenMap);
            k.setChildren(menuList);
        });
    }

    /**
     * 数据转换
     *
     * @param user 系统用户
     * @return SysUserResponse
     */
    private SysUserResponse convert(SysUser user) {
        SysUserResponse resp = new SysUserResponse();
        BeanCopier.copy(user, resp);

        // 查询系统用户与角色关系
        LambdaQueryWrapper<SysRole> sysRoleWrapper = new LambdaQueryWrapper<>();
        sysRoleWrapper.eq(SysRole::getDeleteStatus, Boolean.FALSE);
        sysRoleWrapper.eq(SysRole::getSysUserId, user.getId());
        List<SysRole> sysRoles = sysRoleMapper.selectList(sysRoleWrapper);

        sysRoles.forEach(sysRole -> {
            Role role = roleMapper.selectById(sysRole.getRoleId());
            resp.getRoles().add(role);
        });
        return resp;
    }
}
