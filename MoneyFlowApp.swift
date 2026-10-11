import SwiftUI

@main
struct MoneyFlowApp: App {
    @StateObject private var api = APIClient.shared
    var body: some Scene {
        WindowGroup {
            RootView().environmentObject(api).preferredColorScheme(.dark).tint(MFTheme.accent)
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var api: APIClient
    var body: some View {
        Group {
            if api.token == nil { AuthView() } else { MainTabsView() }
        }
        .background(MFTheme.background.ignoresSafeArea())
    }
}

struct MainTabsView: View {
    var body: some View {
        TabView {
            DashboardView().tabItem { Label("Обзор", systemImage: "square.grid.2x2.fill") }
            WalletsView().tabItem { Label("Кошельки", systemImage: "creditcard.fill") }
            TransactionsView().tabItem { Label("Операции", systemImage: "list.bullet.rectangle") }
            GoalsView().tabItem { Label("Цели", systemImage: "scope") }
            MoreView().tabItem { Label("Ещё", systemImage: "ellipsis.circle.fill") }
        }
    }
}
