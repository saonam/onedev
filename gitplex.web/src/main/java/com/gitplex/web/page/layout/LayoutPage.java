package com.gitplex.web.page.layout;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.head.CssHeaderItem;
import org.apache.wicket.markup.head.IHeaderResponse;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.AbstractLink;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.model.AbstractReadOnlyModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import com.gitplex.core.GitPlex;
import com.gitplex.core.entity.Account;
import com.gitplex.core.entity.Depot;
import com.gitplex.core.manager.ConfigManager;
import com.gitplex.core.manager.DepotManager;
import com.gitplex.core.security.SecurityUtils;
import com.gitplex.web.component.accountselector.OrganizationSelector;
import com.gitplex.web.component.avatar.AvatarLink;
import com.gitplex.web.component.depotselector.DepotSelector;
import com.gitplex.web.page.account.overview.AccountOverviewPage;
import com.gitplex.web.page.account.overview.NewOrganizationPage;
import com.gitplex.web.page.account.setting.ProfileEditPage;
import com.gitplex.web.page.account.tasks.TaskListPage;
import com.gitplex.web.page.admin.SystemSettingPage;
import com.gitplex.web.page.base.BasePage;
import com.gitplex.web.page.depot.file.DepotFilePage;
import com.gitplex.web.page.security.LoginPage;
import com.gitplex.web.page.security.LogoutPage;
import com.gitplex.web.page.security.RegisterPage;
import com.gitplex.web.websocket.TaskChangedRegion;
import com.gitplex.commons.loader.AppLoader;
import com.gitplex.commons.loader.Plugin;
import com.gitplex.commons.wicket.component.DropdownLink;
import com.gitplex.commons.wicket.component.floating.AlignPlacement;
import com.gitplex.commons.wicket.component.menu.MenuItem;
import com.gitplex.commons.wicket.component.menu.MenuLink;
import com.gitplex.commons.wicket.websocket.WebSocketRegion;
import com.gitplex.commons.wicket.websocket.WebSocketRenderBehavior;

@SuppressWarnings("serial")
public abstract class LayoutPage extends BasePage {

	private static final String RELEASE_DATE_FORMAT = "yyyy-MM-dd";
	
	public LayoutPage() {
	}
	
	public LayoutPage(PageParameters params) {
		super(params);
	}
	
	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		WebMarkupContainer head = new WebMarkupContainer("mainHead");
		add(head);
		
		head.add(new BookmarkablePageLink<Void>("home", getApplication().getHomePage()));
		head.add(newContextHead("context"));
		
		head.add(new DropdownLink("organizations") {

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(getLoginUser() != null);
			}

			@Override
			protected Component newContent(String id) {
				return new OrganizationSelector(id, new AbstractReadOnlyModel<Account>() {

					@Override
					public Account getObject() {
						return getLoginUser();
					}

				}, Account.idOf(getAccount())) {

					@Override
					protected void onSelect(AjaxRequestTarget target, Account account) {
						LayoutPage.this.onSelect(target, account);
					}

				};
			}
			
		});
		head.add(new DropdownLink("repositories") {

			@Override
			protected Component newContent(String id) {
				return new DepotSelector(id, new LoadableDetachableModel<Collection<Depot>>() {

					@Override
					protected Collection<Depot> load() {
						return GitPlex.getInstance(DepotManager.class).findAllAccessible(null, getLoginUser());
					}
					
				}, Depot.idOf(getDepot())) {

					@Override
					protected void onSelect(AjaxRequestTarget target, Depot depot) {
						LayoutPage.this.onSelect(target, depot);
					}

				};
			}
			
		});
		
		if (isLoggedIn()) {
			head.add(new DropdownLink("createNewDropdown") {

				@Override
				protected Component newContent(String id) {
					Fragment fragment = new Fragment(id, "createNewFrag", LayoutPage.this);
					fragment.add(new BookmarkablePageLink<Void>("createNewDepot", CreateDepotPage.class));
					fragment.add(new BookmarkablePageLink<Void>("createNewOrganization", 
							NewOrganizationPage.class, NewOrganizationPage.paramsOf(getLoginUser())));
					return fragment;
				}
				
			});
		} else {
			head.add(new WebMarkupContainer("createNewDropdown").setVisible(false));
		}
		
		head.add(new BookmarkablePageLink<Void>("administration", SystemSettingPage.class)
				.setVisible(SecurityUtils.canManageSystem()));
		head.add(new ExternalLink("docLink", GitPlex.getInstance().getDocLink()));
		
		Account user = getLoginUser();
		boolean signedIn = user != null;

		head.add(new Link<Void>("login") {

			@Override
			public void onClick() {
				throw new RestartResponseAtInterceptPageException(LoginPage.class);
			}
			
		}.setVisible(!signedIn));
		
		boolean enableSelfRegister = GitPlex.getInstance(ConfigManager.class).getSecuritySetting().isEnableSelfRegister();
		head.add(new BookmarkablePageLink<Void>("register", RegisterPage.class).setVisible(!signedIn && enableSelfRegister));
		head.add(new BookmarkablePageLink<Void>("logout", LogoutPage.class).setVisible(signedIn));
		if (user != null) {
			head.add(new BookmarkablePageLink<Void>("tasks", TaskListPage.class, TaskListPage.paramsOf(user)) {
	
				@Override
				protected void onInitialize() {
					super.onInitialize();
					
					add(new WebSocketRenderBehavior() {
						
						@Override
						protected void onRender(WebSocketRequestHandler handler) {
							handler.add(getComponent());
						}
						
					});
					
					setOutputMarkupPlaceholderTag(true);
				}

				@Override
				protected void onConfigure() {
					super.onConfigure();
					setVisible(!getLoginUser().getRequestTasks().isEmpty());
				}
				
			});
			
		} else {
			head.add(new WebMarkupContainer("tasks").setVisible(false));
		}
		
		if (signedIn) {
			head.add(new AvatarLink("user", user, null));
			head.add(new MenuLink("userMenuTrigger", new AlignPlacement(50, 100, 50, 0, 8)) {

				@Override
				protected List<MenuItem> getMenuItems() {
					List<MenuItem> menuItems = new ArrayList<>();
					menuItems.add(new MenuItem() {

						@Override
						public String getIconClass() {
							return null;
						}

						@Override
						public String getLabel() {
							return "Profile";
						}

						@Override
						public AbstractLink newLink(String id) {
							return new Link<Void>(id) {

								@Override
								public void onClick() {
									setResponsePage(ProfileEditPage.class, ProfileEditPage.paramsOf(user));									
								}
								
							};
						}
						
					});
					menuItems.add(new MenuItem() {

						@Override
						public String getIconClass() {
							return null;
						}

						@Override
						public String getLabel() {
							return "Logout";
						}

						@Override
						public AbstractLink newLink(String id) {
							return new Link<Void>(id) {

								@Override
								public void onClick() {
									setResponsePage(LogoutPage.class);									
								}
								
							};
						}
						
					});
					return menuItems;
				}
				
			});
		} else {  
			head.add(new WebMarkupContainer("user").setVisible(false));
			head.add(new WebMarkupContainer("userMenuTrigger").setVisible(false));
			head.add(new WebMarkupContainer("userMenu").setVisible(false));
		}
		
		add(new WebMarkupContainer("mainFoot") {

			@Override
			protected void onInitialize() {
				super.onInitialize();
				Plugin product = AppLoader.getProduct();
				add(new Label("productVersion", product.getVersion()));
				add(new Label("releaseDate", new SimpleDateFormat(RELEASE_DATE_FORMAT).format(product.getDate())));
			}

			@Override
			protected void onConfigure() {
				super.onConfigure();
				setVisible(isFootVisible());
			}
			
		});
	}

	protected Component newContextHead(String componentId) {
		return new WebMarkupContainer(componentId);
	}
	
	protected boolean isLoggedIn() {
		return getLoginUser() != null;
	}
	
	protected boolean isRemembered() {
		return SecurityUtils.getSubject().isRemembered();
	}
	
	protected boolean isAuthenticated() {
		return SecurityUtils.getSubject().isAuthenticated();
	}

	protected boolean isFootVisible() {
		return true;
	}
	
	@Nullable
	protected Account getAccount() {
		return getLoginUser();
	}

	@Nullable
	protected Depot getDepot() {
		return null;
	}
	
	protected void onSelect(AjaxRequestTarget target, Account account) {
		setResponsePage(AccountOverviewPage.class, AccountOverviewPage.paramsOf(account));
	}
	
	protected void onSelect(AjaxRequestTarget target, Depot depot) {
		setResponsePage(DepotFilePage.class, DepotFilePage.paramsOf(depot));
	}

	@Override
	public Collection<WebSocketRegion> getWebSocketRegions() {
		Collection<WebSocketRegion> regions = super.getWebSocketRegions();
		if (isLoggedIn()) {
			regions.add(new TaskChangedRegion(getLoginUser().getId()));
		}
		return regions;
	}

	@Override
	public void renderHead(IHeaderResponse response) {
		super.renderHead(response);
		
		response.render(CssHeaderItem.forReference(new LayoutResourceReference()));
	}
	
}