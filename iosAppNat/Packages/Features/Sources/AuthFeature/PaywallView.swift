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

    /// Signs out and goes back to the space choice, like signing out from the settings.
    func signOut() async {
        isLeaving = true
        defer { isLeaving = false }
        await session.logout()
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

                PaywallBenefits()
                    .padding(.bottom, 12)

                PaywallMessages(viewModel: viewModel)

                WrPrimaryButton("I've subscribed – check again", isLoading: viewModel.isChecking) {
                    Task { await viewModel.check() }
                }
                .disabled(viewModel.isChecking || viewModel.isLeaving)
                .accessibilityIdentifier("paywall.checkAgain")

                Button("Sign out") {
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
                subtitle: "Your account is ready. Subscribe to get everything the Writeopia cloud offers:"
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

/// What the subscription brings: the reasons to subscribe.
private struct PaywallBenefits: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            PaywallBenefit(
                systemImage: "arrow.triangle.2.circlepath",
                title: "Sync across all platforms",
                detail: "Write on your computer and keep going on your phone, tablet or the web. Your documents are always up to date, everywhere."
            )
            PaywallBenefit(
                systemImage: "sparkles",
                title: "Cloud AI",
                detail: "Writing suggestions, commands on your text, summaries and header pictures, with nothing to install."
            )
            PaywallBenefit(
                systemImage: "person.2",
                title: "Work as a team",
                detail: "Share workspaces and write together with your team."
            )
            PaywallBenefit(
                systemImage: "lock.icloud",
                title: "Safe in the cloud",
                detail: "Your documents are backed up, so nothing is lost with a device."
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("paywall.benefits")
    }
}

private struct PaywallBenefit: View {
    let systemImage: String
    let title: LocalizedStringKey
    let detail: LocalizedStringKey

    var body: some View {
        HStack(alignment: .top, spacing: 14) {
            Image(systemName: systemImage)
                .font(.title3)
                .foregroundStyle(WrColors.accent)
                .frame(width: 28)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(WrColors.textLighter)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
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
