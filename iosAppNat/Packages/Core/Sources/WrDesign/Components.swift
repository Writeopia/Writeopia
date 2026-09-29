import SwiftUI

/// Full width call to action used across the auth screens.
public struct WrPrimaryButton: View {
    private let title: LocalizedStringKey
    private let isLoading: Bool
    private let action: () -> Void

    public init(_ title: LocalizedStringKey, isLoading: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.isLoading = isLoading
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            ZStack {
                Text(title)
                    .font(.headline)
                    .opacity(isLoading ? 0 : 1)

                if isLoading {
                    ProgressView()
                        .tint(.white)
                }
            }
            .frame(maxWidth: .infinity, minHeight: 28)
        }
        .buttonStyle(.borderedProminent)
        .controlSize(.large)
        .tint(WrColors.accent)
        .disabled(isLoading)
    }
}

/// Rounded text field with a leading icon.
public struct WrTextField: View {
    public enum Kind {
        case plain
        case email
        case password
        case newPassword
        case name
        case code
    }

    private let title: LocalizedStringKey
    private let systemImage: String
    private let kind: Kind
    @Binding private var text: String

    public init(_ title: LocalizedStringKey, text: Binding<String>, systemImage: String, kind: Kind = .plain) {
        self.title = title
        self._text = text
        self.systemImage = systemImage
        self.kind = kind
    }

    public var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .foregroundStyle(WrColors.textLighter)
                .frame(width: 20)

            field
                .textFieldStyle(.plain)
                .autocorrectionDisabled()
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(WrColors.surface, in: RoundedRectangle(cornerRadius: 12))
        .overlay {
            RoundedRectangle(cornerRadius: 12)
                .strokeBorder(WrColors.divider)
        }
    }

    @ViewBuilder
    private var field: some View {
        switch kind {
        case .password:
            SecureField(title, text: $text)
                .textContentType(.password)
        case .newPassword:
            SecureField(title, text: $text)
                .textContentType(.newPassword)
        case .email:
            TextField(title, text: $text)
                .textContentType(.emailAddress)
                #if os(iOS)
                .keyboardType(.emailAddress)
                .textInputAutocapitalization(.never)
                #endif
        case .name:
            TextField(title, text: $text)
                .textContentType(.name)
        case .code:
            TextField(title, text: $text)
                .textContentType(.oneTimeCode)
                #if os(iOS)
                .keyboardType(.numberPad)
                #endif
        case .plain:
            TextField(title, text: $text)
        }
    }
}

/// Inline error text shown under forms.
public struct WrErrorText: View {
    private let message: String?

    public init(_ message: String?) {
        self.message = message
    }

    public var body: some View {
        if let message {
            Label(message, systemImage: "exclamationmark.circle")
                .font(.footnote)
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity, alignment: .leading)
                .transition(.opacity)
        }
    }
}

/// Header used on top of the auth screens.
public struct WrScreenHeader: View {
    private let eyebrow: LocalizedStringKey?
    private let title: LocalizedStringKey
    private let subtitle: LocalizedStringKey?

    public init(eyebrow: LocalizedStringKey? = nil, title: LocalizedStringKey, subtitle: LocalizedStringKey? = nil) {
        self.eyebrow = eyebrow
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let eyebrow {
                Text(eyebrow)
                    .textCase(.uppercase)
                    .font(.caption.weight(.bold))
                    .kerning(2)
                    .foregroundStyle(WrColors.textLighter)
            }

            Text(title)
                .font(.largeTitle.bold())
                .foregroundStyle(WrColors.textLight)

            if let subtitle {
                Text(subtitle)
                    .font(.callout)
                    .foregroundStyle(WrColors.textLighter)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A list of options in a sheet: a plain list on iOS, a grouped form on the Mac, where a list
/// of buttons looks out of place.
public struct WrSheetList<Content: View>: View {
    @ViewBuilder private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        #if os(macOS)
        Form { content }
            .formStyle(.grouped)
        #else
        List { content }
        #endif
    }
}

/// A full-width row of a `WrSheetList`: an icon in a fixed column, a title and, when given,
/// something at the trailing edge. Looks the same on both platforms.
public struct WrSheetRow<Trailing: View>: View {
    private let title: Text
    private let systemImage: String
    private let tint: Color
    private let action: () -> Void
    private let trailing: Trailing

    public init(
        _ title: LocalizedStringKey,
        systemImage: String,
        tint: Color = WrColors.textLight,
        action: @escaping () -> Void,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.title = Text(title)
        self.systemImage = systemImage
        self.tint = tint
        self.action = action
        self.trailing = trailing()
    }

    public init(
        verbatim title: String,
        systemImage: String,
        tint: Color = WrColors.textLight,
        action: @escaping () -> Void,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.title = Text(verbatim: title)
        self.systemImage = systemImage
        self.tint = tint
        self.action = action
        self.trailing = trailing()
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: systemImage)
                    .frame(width: 22)
                title
                Spacer()
                trailing
            }
            .foregroundStyle(tint)
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Loading / error / empty wrapper for screens that fetch content.
public struct WrStateOverlay: View {
    private let isLoading: Bool
    private let isEmpty: Bool
    private let errorMessage: String?
    private let emptyTitle: LocalizedStringKey
    private let emptyImage: String
    private let retry: (() -> Void)?

    public init(
        isLoading: Bool,
        isEmpty: Bool,
        errorMessage: String?,
        emptyTitle: LocalizedStringKey,
        emptyImage: String,
        retry: (() -> Void)? = nil
    ) {
        self.isLoading = isLoading
        self.isEmpty = isEmpty
        self.errorMessage = errorMessage
        self.emptyTitle = emptyTitle
        self.emptyImage = emptyImage
        self.retry = retry
    }

    public var body: some View {
        if isLoading && isEmpty {
            ProgressView()
                .controlSize(.large)
        } else if let errorMessage, isEmpty {
            ContentUnavailableView {
                Label("Something went wrong", systemImage: "exclamationmark.triangle")
            } description: {
                Text(errorMessage)
            } actions: {
                if let retry {
                    Button("Try again", action: retry)
                }
            }
        } else if !isLoading && isEmpty {
            ContentUnavailableView(emptyTitle, systemImage: emptyImage)
        }
    }
}
