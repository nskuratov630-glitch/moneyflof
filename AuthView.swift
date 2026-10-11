import SwiftUI

struct AuthView: View {
    @EnvironmentObject private var api: APIClient
    @State private var email = ""
    @State private var password = ""
    @State private var register = false
    @State private var loading = false
    @State private var error: String?
    @State private var showSettings = false
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    Spacer(minLength: 28)
                    Image(systemName: "wallet.bifold.fill").font(.system(size: 44, weight: .bold)).foregroundStyle(MFTheme.accent).padding(18).background(MFTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 22))
                    VStack(alignment: .leading, spacing: 8) { Text("MoneyFlow").font(.system(size: 36, weight: .bold, design: .rounded)); Text("Твои деньги — под контролем").foregroundStyle(MFTheme.muted) }
                    MFCard {
                        Text(register ? "Создать аккаунт" : "С возвращением").font(.title2.bold())
                        TextField("Электронная почта", text: $email).textContentType(.emailAddress).keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled().padding(13).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                        SecureField("Пароль (минимум 10 символов)", text: $password).textContentType(register ? .newPassword : .password).padding(13).background(.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 12))
                        if let error { Text(error).font(.footnote).foregroundStyle(.red) }
                        Button { Task { await submit() } } label: { HStack { Spacer(); if loading { ProgressView() } else { Text(register ? "Зарегистрироваться" : "Войти").bold() }; Spacer() }.padding(14).background(MFTheme.accent, in: RoundedRectangle(cornerRadius: 13)).foregroundStyle(.black) }.disabled(loading)
                        Button(register ? "Уже есть аккаунт? Войти" : "Создать новый аккаунт") { register.toggle(); error = nil }.frame(maxWidth: .infinity).font(.subheadline)
                    }
                    Button("Настроить адрес сервера") { showSettings = true }.font(.footnote).foregroundStyle(MFTheme.muted).frame(maxWidth: .infinity)
                    Text("MoneyFlow хранит записи на твоём сервере. Приложение не переводит реальные деньги и не подключается к банкам.").font(.caption).foregroundStyle(MFTheme.muted)
                }.padding(22)
            }
            .background(MFTheme.background)
            .toolbar(.hidden, for: .navigationBar)
            .sheet(isPresented: $showSettings) { APISettingsView() }
        }
    }
    private func submit() async {
        error = nil
        guard email.contains("@"), password.count >= (register ? 10 : 1) else { error = register ? "Проверь email и пароль длиной не менее 10 символов." : "Введи корректный email и пароль."; return }
        loading = true; defer { loading = false }
        do { _ = try await api.authenticate(email: email.trimmingCharacters(in: .whitespacesAndNewlines), password: password, register: register) }
        catch { self.error = error.localizedDescription }
    }
}

struct APISettingsView: View {
    @EnvironmentObject private var api: APIClient
    @Environment(\.dismiss) private var dismiss
    @State private var url = ""
    var body: some View {
        NavigationStack { Form { Section("Адрес API") { TextField("https://api.example.com/", text: $url).textInputAutocapitalization(.never).autocorrectionDisabled(); Text("Для удалённого сервера обязательно HTTPS. Для локальной разработки используй адрес localhost.").font(.caption).foregroundStyle(.secondary) } }.navigationTitle("Подключение").toolbar { ToolbarItem(placement: .confirmationAction) { Button("Сохранить") { api.setBaseURL(url.trimmingCharacters(in: .whitespacesAndNewlines)); dismiss() } }; ToolbarItem(placement: .cancellationAction) { Button("Отмена") { dismiss() } } }.onAppear { url = api.baseURL } }
    }
}
