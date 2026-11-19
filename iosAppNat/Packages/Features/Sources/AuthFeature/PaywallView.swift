import Observation
import SwiftUI
import WrDesign
import WrModels
import WrNetwork
import WrSession

@Observable
final class PaywallViewModel {
    private(set) var isChecking = false
    private(set) var errorMessage: String?
    /// The user asked again and the backend still says the account isn't premium.
    private(set) var stillNotPremium = false
    private(set) var isLeaving = false

    private let session: AppSession
    private let locale: Locale

    init(session: AppSession, locale: Locale = .current) {
        self.session = session
        self.locale = locale
    }

    /// The paywall waits for an answer of the backend before showing itself, so a premium user
    /// asked at launch only sees the check.
    var isWaitingForFirstAnswer: Bool { session.subscription == nil && errorMessage == nil }

    /// "$5.00", written for the user's locale; nil while unknown, so the price line is hidden.
    var monthlyPrice: String? { session.subscription?.formattedMonthlyPrice(locale: locale) }

    /// Asks the backend when nothing is known yet: at launch, or after a failed check at login.
    func start() async {
        guard session.subscription == nil, !isChecking else { return }
        await check()
    }

    /// "I've subscribed – check again". Premium: the session goes on past the paywall.
    func check() async {
        isChecking = true
        errorMessage = nil
        stillNotPremium = false
        defer { isChecking = false }

        do {
            let status = try await session.checkSubscription()
            stillNotPremium = !status.premium
        } catch {
            errorMessage = error.userMessage
        }
    }

    /// Signs the online account out and opens the private space.
    func useOfflineSpace() async {
        isLeaving = true
        defer { isLeaving = false }
        await session.useOfflineSpaceInstead()
    }

    /// Signs out and goes back to the login screen.
    func signOut() async {
        isLeaving = true
        defer { isLeaving = false }
        await session.signOutToLogin()
    }
}

/// Shown after signing in, or at launch, while the backend doesn't say the user is premium: the
/// cloud account needs a subscription. Offers to check again, the private space, or another account.
public struct PaywallView: View {
    @State private var viewModel: PaywallViewModel

    public init(session: AppSession) {
        _viewModel = State(initialValue: PaywallViewModel(session: session))
    }

    public var body: some View {
        Group {
            if viewModel.isWaitingForFirstAnswer {
                PaywallCheckingView()
            } else {
                PaywallContent(viewModel: viewModel)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(WrColors.background)
        .task { await viewModel.start() }
    }
}

private struct PaywallCheckingView: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .controlSize(.large)
            Text("Checking your subscription…")
                .font(.callout)
                .foregroundStyle(WrColors.textLighter)
        }
        .accessibilityIdentifier("paywall.checking")
    }
}

private struct PaywallContent: View {
    let viewModel: PaywallViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                PaywallHeader(monthlyPrice: viewModel.monthlyPrice)
                    .padding(.bottom, 12)

                PaywallMessages(viewModel: viewModel)

                WrPrimaryButton("I've subscribed – check again", isLoading: viewModel.isChecking) {
                    Task { await viewModel.check() }
                }
                .disabled(viewModel.isChecking || viewModel.isLeaving)
                .accessibilityIdentifier("paywall.checkAgain")

                PaywallOfflineOption(viewModel: viewModel)

                Button("Sign out and use another account") {
                    Task { await viewModel.signOut() }
                }
                .font(.callout.weight(.semibold))
                .tint(WrColors.accent)
                .disabled(viewModel.isLeaving)
                .accessibilityIdentifier("paywall.signOut")
            }
            .padding(24)
            .frame(maxWidth: 520)
            .frame(maxWidth: .infinity)
        }
        .animation(.default, value: viewModel.errorMessage)
        .animation(.default, value: viewModel.stillNotPremium)
    }
}

private struct PaywallHeader: View {
    let monthlyPrice: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            WrScreenHeader(
                eyebrow: "Open space",
                title: "Subscribe to use your cloud account",
                subtitle: "Your Writeopia cloud account needs a subscription: syncing across devices, the cloud AI and team workspaces come with it."
            )

            if let monthlyPrice {
                Text("\(monthlyPrice) per month")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(WrColors.accent)
                    .accessibilityIdentifier("paywall.price")
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The error of the last check with a retry, or that the account still isn't premium.
private struct PaywallMessages: View {
    let viewModel: PaywallViewModel

    var body: some View {
        if let errorMessage = viewModel.errorMessage {
            VStack(alignment: .leading, spacing: 8) {
                Text("We couldn't check your subscription.")
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.red)
                WrErrorText(errorMessage)
                Button("Try again") {
                    Task { await viewModel.check() }
                }
                .font(.footnote.weight(.semibold))
                .tint(WrColors.accent)
                .disabled(viewModel.isChecking)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("paywall.error")
        } else if viewModel.stillNotPremium {
            Label("This account doesn't have a subscription yet.", systemImage: "info.circle")
                .font(.footnote)
                .foregroundStyle(WrColors.textLighter)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier("paywall.stillFree")
        }
    }
}

private struct PaywallOfflineOption: View {
    let viewModel: PaywallViewModel

    var body: some View {
        Button {
            Task { await viewModel.useOfflineSpace() }
        } label: {
            VStack(alignment: .leading, spacing: 6) {
                Label("Try the free version – use the offline space", systemImage: "lock.shield")
                    .font(.callout.weight(.semibold))
                    .foregroundStyle(WrColors.textLight)
                Text("Your documents stay on this device.")
                    .font(.footnote)
                    .foregroundStyle(WrColors.textLighter)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(WrColors.divider)
            }
            .contentShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isLeaving)
        .accessibilityIdentifier("paywall.offline")
    }
}
