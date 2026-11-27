import SwiftUI

// Components of the v4 redesign. Each one matches a component of the Figma library (page
// "01 Components · v4") with the same name without the `Wr` prefix: `WrPill` is `v4/Pill`.
// They draw with `WrTheme` tokens only, so light and dark come from the appearance.

public extension EnvironmentValues {
    /// True inside screens already on the v4 redesign: the shared form pieces (`WrTextField`,
    /// `WrPrimaryButton`, `WrBackButton`, `WrScreenHeader`) switch to the v4 look.
    @Entry var wrUsesV4: Bool = false
}

public extension View {
    /// Marks a screen as v4, see `EnvironmentValues.wrUsesV4`.
    func wrV4() -> some View {
        environment(\.wrUsesV4, true)
    }
}

// MARK: - Button

/// `v4/Button`.
public struct WrButton: View {
    public enum Kind: Sendable {
        case primary
        case secondary
        case ghost
        case danger
    }

    public enum Size: Sendable {
        /// 32 pt, for toolbars, rows and dialogs.
        case medium
        /// 40 pt, for the main action of a screen.
        case large
    }

    private let title: LocalizedStringKey
    private let systemImage: String?
    private let kind: Kind
    private let size: Size
    private let fillsWidth: Bool
    private let isLoading: Bool
    private let action: () -> Void

    public init(
        _ title: LocalizedStringKey,
        systemImage: String? = nil,
        kind: Kind = .secondary,
        size: Size = .medium,
        fillsWidth: Bool = false,
        isLoading: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.systemImage = systemImage
        self.kind = kind
        self.size = size
        self.fillsWidth = fillsWidth
        self.isLoading = isLoading
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            ZStack {
                HStack(spacing: 6) {
                    if let systemImage {
                        Image(systemName: systemImage)
                            .imageScale(.small)
                    }
                    Text(title)
                }
                .opacity(isLoading ? 0 : 1)

                if isLoading {
                    WrSpinner(size: 14, tint: kind == .primary ? WrTheme.Colors.textOnAccent : WrTheme.Colors.accent)
                }
            }
        }
        .buttonStyle(WrButtonStyle(kind: kind, size: size, fillsWidth: fillsWidth))
        .disabled(isLoading)
    }
}

/// The look of `WrButton`, usable on any `Button`.
public struct WrButtonStyle: ButtonStyle {
    let kind: WrButton.Kind
    let size: WrButton.Size
    let fillsWidth: Bool

    public init(kind: WrButton.Kind, size: WrButton.Size = .medium, fillsWidth: Bool = false) {
        self.kind = kind
        self.size = size
        self.fillsWidth = fillsWidth
    }

    public func makeBody(configuration: Configuration) -> some View {
        WrButtonBody(configuration: configuration, kind: kind, size: size, fillsWidth: fillsWidth)
    }
}

private struct WrButtonBody: View {
    let configuration: ButtonStyleConfiguration
    let kind: WrButton.Kind
    let size: WrButton.Size
    let fillsWidth: Bool
    @Environment(\.isEnabled) private var isEnabled
    @State private var isHovered = false

    var body: some View {
        configuration.label
            .wrTextStyle(size == .large ? .labelLarge : .labelMedium)
            .lineLimit(1)
            .foregroundStyle(foreground)
            .padding(.horizontal, size == .large ? WrTheme.Space.s200 : WrTheme.Space.s150)
            .frame(maxWidth: fillsWidth ? .infinity : nil)
            .frame(height: size == .large ? WrTheme.Size.controlLg : WrTheme.Size.controlMd)
            .background(background, in: shape)
            .overlay {
                if let border {
                    shape.strokeBorder(border)
                }
            }
            .contentShape(shape)
            .opacity(isEnabled ? 1 : 0.4)
            .onHover { isHovered = $0 }
            .animation(.easeOut(duration: 0.12), value: isHovered)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: WrTheme.Radius.md)
    }

    private var isPressed: Bool { configuration.isPressed }
    private var isActive: Bool { isHovered && isEnabled }

    private var foreground: Color {
        switch kind {
        case .primary: WrTheme.Colors.textOnAccent
        case .secondary, .ghost: WrTheme.Colors.textPrimary
        case .danger: WrTheme.Colors.danger
        }
    }

    private var background: Color {
        switch kind {
        case .primary:
            isPressed ? WrTheme.Colors.accentPressed : isActive ? WrTheme.Colors.accentHover : WrTheme.Colors.accent
        case .secondary:
            isPressed ? WrTheme.Colors.bgPressed : WrTheme.Colors.bgHover
        case .ghost:
            isPressed ? WrTheme.Colors.bgPressed : isActive ? WrTheme.Colors.bgHover : .clear
        case .danger:
            isPressed || isActive ? WrTheme.Colors.dangerSubtle : .clear
        }
    }

    private var border: Color? {
        switch kind {
        case .primary, .ghost: nil
        case .secondary: WrTheme.Colors.borderSubtle
        case .danger: WrTheme.Colors.danger
        }
    }
}

// MARK: - Feedback

/// `v4/Pill`: a status with a dot.
public struct WrPill: View {
    public enum Kind: Sendable {
        case success
        case warning
        case info
        case danger
        case accent
        case ai
        case neutral
    }

    private let title: LocalizedStringKey
    private let kind: Kind
    private let showsDot: Bool

    public init(_ title: LocalizedStringKey, kind: Kind, showsDot: Bool = true) {
        self.title = title
        self.kind = kind
        self.showsDot = showsDot
    }

    public var body: some View {
        HStack(spacing: WrTheme.Space.s50) {
            if showsDot {
                Circle()
                    .fill(foreground)
                    .frame(width: 6, height: 6)
            }
            Text(title)
                .wrTextStyle(.labelSmall)
                .lineLimit(1)
        }
        .foregroundStyle(foreground)
        .padding(.horizontal, WrTheme.Space.s100)
        .padding(.vertical, 2)
        .background(background, in: Capsule())
    }

    private var foreground: Color {
        switch kind {
        case .success: WrTheme.Colors.success
        case .warning: WrTheme.Colors.warning
        case .info: WrTheme.Colors.info
        case .danger: WrTheme.Colors.danger
        case .accent: WrTheme.Colors.accentOnSubtle
        case .ai: WrTheme.Colors.aiOnSubtle
        case .neutral: WrTheme.Colors.textSecondary
        }
    }

    private var background: Color {
        switch kind {
        case .success: WrTheme.Colors.successSubtle
        case .warning: WrTheme.Colors.warningSubtle
        case .info: WrTheme.Colors.infoSubtle
        case .danger: WrTheme.Colors.dangerSubtle
        case .accent: WrTheme.Colors.accentSubtle
        case .ai: WrTheme.Colors.aiSubtle
        case .neutral: WrTheme.Colors.bgHover
        }
    }
}

/// `v4/Radio`: the control only. The row around it handles the tap.
public struct WrRadio: View {
    private let isOn: Bool
    @Environment(\.isEnabled) private var isEnabled

    public init(isOn: Bool) {
        self.isOn = isOn
    }

    public var body: some View {
        Circle()
            .fill(WrTheme.Colors.bgRaised)
            .overlay {
                Circle()
                    .strokeBorder(isOn ? WrTheme.Colors.accent : WrTheme.Colors.borderStrong, lineWidth: isOn ? 5 : 1.5)
            }
            .frame(width: 16, height: 16)
            .opacity(isEnabled ? 1 : 0.4)
            .accessibilityHidden(true)
    }
}

/// `v4/Spinner`.
public struct WrSpinner: View {
    private let size: CGFloat
    private let tint: Color
    @State private var isSpinning = false

    public init(size: CGFloat = 16, tint: Color = WrTheme.Colors.accent) {
        self.size = size
        self.tint = tint
    }

    public var body: some View {
        let lineWidth = max(1.5, size * 0.14)
        ZStack {
            Circle()
                .stroke(WrTheme.Colors.borderDefault, lineWidth: lineWidth)
            Circle()
                .trim(from: 0, to: 0.3)
                .stroke(tint, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(isSpinning ? 360 : 0))
        }
        .frame(width: size, height: size)
        .onAppear {
            withAnimation(.linear(duration: 0.9).repeatForever(autoreverses: false)) {
                isSpinning = true
            }
        }
        .accessibilityLabel(Text("Loading"))
    }
}

/// `v4/ProgressBar`. `value` is 0...1; nil while the total isn't known yet.
public struct WrProgressBar: View {
    private let value: Double?

    public init(value: Double?) {
        self.value = value
    }

    public var body: some View {
        Capsule()
            .fill(WrTheme.Colors.bgSunken)
            .overlay(alignment: .leading) {
                GeometryReader { proxy in
                    Capsule()
                        .fill(WrTheme.Colors.accent)
                        .frame(width: max(6, proxy.size.width * min(1, max(0, value ?? 0))))
                }
            }
            .frame(height: 6)
            .animation(.easeOut(duration: 0.25), value: value)
            .accessibilityElement()
            .accessibilityValue(Text(value.map { $0.formatted(.percent.precision(.fractionLength(0))) } ?? ""))
    }
}

/// `v4/StatusBanner`: something is happening elsewhere, wait here.
public struct WrStatusBanner: View {
    private let title: LocalizedStringKey

    public init(_ title: LocalizedStringKey) {
        self.title = title
    }

    public var body: some View {
        HStack(spacing: WrTheme.Space.s100) {
            WrSpinner()
            Text(title)
                .wrTextStyle(.labelMedium)
                .foregroundStyle(WrTheme.Colors.textSecondary)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, WrTheme.Space.s200)
        .padding(.vertical, WrTheme.Space.s150)
        .background(WrTheme.Colors.bgSubtle, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
    }
}

// MARK: - Content

/// `v4/Chip`: an identifier, like a model name.
public struct WrChip: View {
    private let title: String

    public init(_ title: String) {
        self.title = title
    }

    public var body: some View {
        Text(verbatim: title)
            .wrTextStyle(.code)
            .foregroundStyle(WrTheme.Colors.textSecondary)
            .padding(.horizontal, 10)
            .padding(.vertical, WrTheme.Space.s50)
            .background(WrTheme.Colors.bgHover, in: Capsule())
    }
}

/// `v4/CheckItem`.
public struct WrCheckItem: View {
    private let title: LocalizedStringKey

    public init(_ title: LocalizedStringKey) {
        self.title = title
    }

    public var body: some View {
        HStack(spacing: WrTheme.Space.s100) {
            Image(systemName: "checkmark")
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(WrTheme.Colors.accent)
            Text(title)
                .wrTextStyle(.bodySmall)
                .foregroundStyle(WrTheme.Colors.textSecondary)
        }
    }
}

/// `v4/Divider`.
public struct WrDivider: View {
    public init() {}

    public var body: some View {
        Rectangle()
            .fill(WrTheme.Colors.borderSubtle)
            .frame(height: 1)
    }
}

/// `Brand/Mark`: the four dots of the Writeopia logo, from the Figma vector (86.8 pt box).
/// The two top dots are `brand/ink`, so the mark follows the appearance it's drawn in, like
/// `Brand/Mark · On dark`.
public struct WrBrandMark: View {
    private let size: CGFloat

    public init(size: CGFloat = 32) {
        self.size = size
    }

    public var body: some View {
        Canvas { context, canvasSize in
            let scale = canvasSize.width / Self.box
            context.scaleBy(x: scale, y: scale)

            let radius: CGFloat = 17.364
            func dot(_ x: CGFloat, _ y: CGFloat) -> Path {
                Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
            }
            context.fill(dot(17.364, 17.364), with: .color(WrTheme.Colors.brandInk))
            context.fill(dot(69.456, 17.364), with: .color(WrTheme.Colors.brandInk))
            context.fill(dot(17.364, 69.456), with: .color(Self.violet))
            context.fill(dot(69.456, 69.456), with: .color(Self.magenta))

            context.fill(
                Self.verticalLink,
                with: .linearGradient(
                    Gradient(colors: [Self.violet, Self.ink]),
                    startPoint: CGPoint(x: 17.364, y: 58.604),
                    endPoint: CGPoint(x: 17.364, y: 28.22)
                )
            )
            context.fill(
                Self.horizontalLink,
                with: .linearGradient(
                    Gradient(colors: [Self.violet, Self.magenta]),
                    startPoint: CGPoint(x: 28.2161, y: 69.456),
                    endPoint: CGPoint(x: 58.6001, y: 69.456)
                )
            )
        }
        .frame(width: size, height: size)
        .accessibilityLabel("Writeopia")
    }

    private static let box: CGFloat = 86.8
    private static let violet = Color(red: 0x66 / 255, green: 0x2D / 255, blue: 0x91 / 255)
    private static let magenta = Color(red: 0xFF / 255, green: 0x55 / 255, blue: 0xF3 / 255)
    private static let ink = Color(red: 0x38 / 255, green: 0x38 / 255, blue: 0x38 / 255)

    private static func pt(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x, y: y) }

    /// Joins the top left dot to the bottom left one.
    private static let verticalLink: Path = {
        var p = Path()
        p.move(to: pt(6.50781, 30.904))
        p.addCurve(to: pt(13.0198, 43.408), control1: pt(10.4758, 33.688), control2: pt(13.0198, 38.612))
        p.addCurve(to: pt(6.50781, 55.9), control1: pt(13.0198, 48.204), control2: pt(10.4758, 53.116))
        p.addCurve(to: pt(17.3638, 58.604), control1: pt(9.48381, 57.988), control2: pt(13.2558, 58.604))
        p.addCurve(to: pt(28.2158, 55.92), control1: pt(21.4678, 58.604), control2: pt(25.2398, 58.004))
        p.addCurve(to: pt(21.7038, 43.408), control1: pt(24.2478, 53.136), control2: pt(21.7038, 48.204))
        p.addCurve(to: pt(28.2158, 30.92), control1: pt(21.7038, 38.612), control2: pt(24.2478, 33.704))
        p.addCurve(to: pt(17.3638, 28.216), control1: pt(25.2398, 28.832), control2: pt(21.4678, 28.216))
        p.addCurve(to: pt(6.50781, 30.904), control1: pt(13.2558, 28.216), control2: pt(9.48381, 28.82))
        p.closeSubpath()
        return p
    }()

    /// Joins the bottom left dot to the bottom right one.
    private static let horizontalLink: Path = {
        var p = Path()
        p.move(to: pt(43.4081, 65.116))
        p.addCurve(to: pt(30.9201, 58.604), control1: pt(38.6121, 65.116), control2: pt(33.7041, 62.572))
        p.addCurve(to: pt(28.2161, 69.456), control1: pt(28.8321, 61.576), control2: pt(28.2161, 65.348))
        p.addCurve(to: pt(30.9001, 80.308), control1: pt(28.2161, 73.564), control2: pt(28.8121, 77.336))
        p.addCurve(to: pt(43.4081, 73.796), control1: pt(33.6841, 76.34), control2: pt(38.6121, 73.796))
        p.addCurve(to: pt(55.9001, 80.308), control1: pt(48.2041, 73.796), control2: pt(53.1161, 76.34))
        p.addCurve(to: pt(58.6041, 69.456), control1: pt(57.9841, 77.336), control2: pt(58.6041, 73.564))
        p.addCurve(to: pt(55.9121, 58.604), control1: pt(58.6041, 65.348), control2: pt(58.0001, 61.576))
        p.addCurve(to: pt(43.4081, 65.116), control1: pt(53.1281, 62.572), control2: pt(48.2041, 65.116))
        p.closeSubpath()
        return p
    }()
}

/// `v4/DialogHeader`.
public struct WrDialogHeader: View {
    private let title: LocalizedStringKey
    private let description: LocalizedStringKey?
    private let close: (() -> Void)?

    public init(_ title: LocalizedStringKey, description: LocalizedStringKey? = nil, close: (() -> Void)? = nil) {
        self.title = title
        self.description = description
        self.close = close
    }

    public var body: some View {
        // The close button sits over the top right corner, beside the title only, so the
        // description runs the whole width of the dialog like in Figma.
        VStack(alignment: .leading, spacing: WrTheme.Space.s50) {
            Text(title)
                .wrTextStyle(.headingMedium)
                .foregroundStyle(WrTheme.Colors.textPrimary)
                .padding(.trailing, close == nil ? 0 : 24 + WrTheme.Space.s200)
            if let description {
                Text(description)
                    .wrTextStyle(.bodySmall)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .overlay(alignment: .topTrailing) {
            if let close {
                Button(action: close) {
                    Image(systemName: "xmark")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(WrTheme.Colors.iconSecondary)
                        .frame(width: 24, height: 24)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .keyboardShortcut(.cancelAction)
                .accessibilityLabel(Text("Close"))
            }
        }
    }
}

/// An eyebrow, a title and a subtitle on top of a screen or card, in the v4 type scale.
public struct WrTitleBlock: View {
    private let eyebrow: LocalizedStringKey?
    private let title: LocalizedStringKey
    private let subtitle: LocalizedStringKey?
    private let eyebrowColor: Color
    private let titleStyle: WrTextStyle
    private let alignment: HorizontalAlignment

    public init(
        eyebrow: LocalizedStringKey? = nil,
        title: LocalizedStringKey,
        subtitle: LocalizedStringKey? = nil,
        eyebrowColor: Color = WrTheme.Colors.textTertiary,
        titleStyle: WrTextStyle = .headingLarge,
        alignment: HorizontalAlignment = .leading
    ) {
        self.eyebrow = eyebrow
        self.title = title
        self.subtitle = subtitle
        self.eyebrowColor = eyebrowColor
        self.titleStyle = titleStyle
        self.alignment = alignment
    }

    public var body: some View {
        VStack(alignment: alignment, spacing: WrTheme.Space.s100) {
            if let eyebrow {
                Text(eyebrow)
                    .textCase(.uppercase)
                    .wrTextStyle(.overline)
                    .foregroundStyle(eyebrowColor)
            }
            Text(title)
                .wrTextStyle(titleStyle)
                .foregroundStyle(WrTheme.Colors.textPrimary)
            if let subtitle {
                Text(subtitle)
                    .wrTextStyle(.bodyMedium)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .multilineTextAlignment(alignment == .center ? .center : .leading)
        .frame(maxWidth: .infinity, alignment: Alignment(horizontal: alignment, vertical: .center))
    }
}

// MARK: - Surfaces

/// The card the onboarding screens sit in: raised, bordered, 32 pt inside.
public struct WrCard<Content: View>: View {
    private let padding: CGFloat
    @ViewBuilder private let content: Content

    public init(padding: CGFloat = WrTheme.Space.s400, @ViewBuilder content: () -> Content) {
        self.padding = padding
        self.content = content()
    }

    public var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(WrTheme.Colors.bgRaised, in: RoundedRectangle(cornerRadius: WrTheme.Radius.xl))
            .overlay {
                RoundedRectangle(cornerRadius: WrTheme.Radius.xl)
                    .strokeBorder(WrTheme.Colors.borderSubtle)
            }
            .wrElevation(.raised)
    }
}

/// A full-window onboarding step: the app background, an optional way back at the top and a
/// card in the middle. Scrolls when the window is short.
public struct WrOnboardingScreen<Content: View>: View {
    private let width: CGFloat
    private let back: (title: LocalizedStringKey, action: () -> Void)?
    @ViewBuilder private let content: Content

    public init(
        width: CGFloat = 520,
        back: (title: LocalizedStringKey, action: () -> Void)? = nil,
        @ViewBuilder content: () -> Content
    ) {
        self.width = width
        self.back = back
        self.content = content()
    }

    public var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: 0) {
                    if let back {
                        WrBackButton(back.title, action: back.action)
                            .padding(.bottom, WrTheme.Space.s300)
                    }
                    Spacer(minLength: WrTheme.Space.s300)
                    WrCard { content }
                        .frame(maxWidth: width)
                        .frame(maxWidth: .infinity)
                    Spacer(minLength: WrTheme.Space.s300)
                }
                .padding(WrTheme.Space.s300)
                // At least the window's height, so the card sits in the middle.
                .frame(minHeight: proxy.size.height)
            }
        }
        .background(WrTheme.Colors.bgApp)
        .wrV4()
    }
}

// MARK: - Selection

/// `v4/OptionCard`: one choice of a group, with an icon, a status and an optional action.
public struct WrOptionCard<Action: View>: View {
    public enum IconStyle: Sendable {
        case neutral
        case ai
    }

    private let title: LocalizedStringKey
    private let description: LocalizedStringKey
    private let systemImage: String
    private let iconStyle: IconStyle
    private let isSelected: Bool
    private let status: WrPill?
    private let meta: Text?
    private let onSelect: () -> Void
    @ViewBuilder private let action: Action
    @State private var isHovered = false

    public init(
        _ title: LocalizedStringKey,
        description: LocalizedStringKey,
        systemImage: String,
        iconStyle: IconStyle = .neutral,
        isSelected: Bool,
        status: WrPill? = nil,
        meta: Text? = nil,
        onSelect: @escaping () -> Void,
        @ViewBuilder action: () -> Action = { EmptyView() }
    ) {
        self.title = title
        self.description = description
        self.systemImage = systemImage
        self.iconStyle = iconStyle
        self.isSelected = isSelected
        self.status = status
        self.meta = meta
        self.onSelect = onSelect
        self.action = action()
    }

    public var body: some View {
        HStack(alignment: .top, spacing: WrTheme.Space.s150) {
            // The choice itself is a button, so the trailing action stays its own control.
            Button(action: onSelect) {
                HStack(alignment: .top, spacing: WrTheme.Space.s150) {
                    WrRadio(isOn: isSelected)
                        .padding(.top, 2)

                    Image(systemName: systemImage)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(iconStyle == .ai ? WrTheme.Colors.ai : WrTheme.Colors.iconSecondary)
                        .frame(width: 32, height: 32)
                        .background(
                            iconStyle == .ai ? WrTheme.Colors.aiSubtle : WrTheme.Colors.bgHover,
                            in: RoundedRectangle(cornerRadius: WrTheme.Radius.md)
                        )

                    VStack(alignment: .leading, spacing: WrTheme.Space.s50) {
                        Text(title)
                            .wrTextStyle(.headingSmall)
                            .foregroundStyle(WrTheme.Colors.textPrimary)
                        Text(description)
                            .wrTextStyle(.bodySmall)
                            .foregroundStyle(WrTheme.Colors.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)

                        if status != nil || meta != nil {
                            HStack(spacing: WrTheme.Space.s100) {
                                status
                                meta?
                                    .wrTextStyle(.bodySmall)
                                    .foregroundStyle(WrTheme.Colors.textTertiary)
                                    .lineLimit(1)
                            }
                            .padding(.top, WrTheme.Space.s50)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .multilineTextAlignment(.leading)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(isSelected ? .isSelected : [])

            action
        }
        .padding(WrTheme.Space.s200)
        .background(isSelected ? WrTheme.Colors.accentSubtle : WrTheme.Colors.bgRaised, in: shape)
        .overlay {
            shape.strokeBorder(
                isSelected ? WrTheme.Colors.accent : (isHovered ? WrTheme.Colors.borderStrong : WrTheme.Colors.borderDefault),
                lineWidth: isSelected ? 1.5 : 1
            )
        }
        .contentShape(shape)
        .onHover { isHovered = $0 }
        .animation(.easeOut(duration: 0.15), value: isSelected)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: WrTheme.Radius.lg)
    }
}

/// `v4/TierCard`: one model size in "Configure local AI".
public struct WrTierCard: View {
    private let title: LocalizedStringKey
    private let description: LocalizedStringKey
    private let model: String
    private let isSelected: Bool
    private let isRecommended: Bool
    private let action: () -> Void

    public init(
        _ title: LocalizedStringKey,
        description: LocalizedStringKey,
        model: String,
        isSelected: Bool,
        isRecommended: Bool = false,
        action: @escaping () -> Void
    ) {
        self.title = title
        self.description = description
        self.model = model
        self.isSelected = isSelected
        self.isRecommended = isRecommended
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: WrTheme.Space.s100) {
                HStack(spacing: WrTheme.Space.s100) {
                    WrRadio(isOn: isSelected)
                    Text(title)
                        .wrTextStyle(.headingSmall)
                        .foregroundStyle(WrTheme.Colors.textPrimary)
                }
                Text(description)
                    .wrTextStyle(.bodySmall)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                Text(verbatim: model)
                    .wrTextStyle(.code)
                    .foregroundStyle(WrTheme.Colors.textTertiary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                if isRecommended {
                    WrPill("Recommended", kind: .accent)
                }
            }
            .padding(WrTheme.Space.s200)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(isSelected ? WrTheme.Colors.accentSubtle : WrTheme.Colors.bgRaised, in: shape)
            .overlay {
                shape.strokeBorder(isSelected ? WrTheme.Colors.accent : WrTheme.Colors.borderDefault, lineWidth: isSelected ? 1.5 : 1)
            }
            .contentShape(shape)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: WrTheme.Radius.lg)
    }
}

/// `v4/ProviderRow`: a local AI provider, detected or not.
public struct WrProviderRow<Trailing: View>: View {
    private let name: String
    private let isSelected: Bool
    private let isAvailable: Bool
    private let status: WrPill
    private let action: () -> Void
    @ViewBuilder private let trailing: Trailing

    /// - Parameter isAvailable: false greys out the choice; the trailing action stays usable,
    ///   for "How to install".
    public init(
        _ name: String,
        isSelected: Bool,
        isAvailable: Bool = true,
        status: WrPill,
        action: @escaping () -> Void,
        @ViewBuilder trailing: () -> Trailing = { EmptyView() }
    ) {
        self.name = name
        self.isSelected = isSelected
        self.isAvailable = isAvailable
        self.status = status
        self.action = action
        self.trailing = trailing()
    }

    public var body: some View {
        HStack(spacing: WrTheme.Space.s150) {
            Button(action: action) {
                HStack(spacing: WrTheme.Space.s150) {
                    WrRadio(isOn: isSelected)
                    Text(verbatim: name)
                        .wrTextStyle(.labelMedium)
                        .foregroundStyle(isSelected ? WrTheme.Colors.textPrimary : WrTheme.Colors.textSecondary)
                    status
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .disabled(!isAvailable)

            trailing
        }
        .padding(.horizontal, WrTheme.Space.s200)
        .padding(.vertical, WrTheme.Space.s150)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}

/// `v4/WorkspaceRow`.
public struct WrWorkspaceRow: View {
    private let name: String
    private let meta: Text
    private let badge: WrPill?
    private let action: () -> Void
    @State private var isHovered = false

    public init(_ name: String, meta: Text, badge: WrPill? = nil, action: @escaping () -> Void) {
        self.name = name
        self.meta = meta
        self.badge = badge
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: WrTheme.Space.s150) {
                Text(verbatim: String(name.prefix(1)).uppercased())
                    .wrTextStyle(.labelMedium)
                    .foregroundStyle(WrTheme.Colors.textOnAccent)
                    .frame(width: 32, height: 32)
                    .background(WrTheme.Colors.accent, in: RoundedRectangle(cornerRadius: WrTheme.Radius.md))

                VStack(alignment: .leading, spacing: 2) {
                    Text(verbatim: name)
                        .wrTextStyle(.labelMedium)
                        .foregroundStyle(WrTheme.Colors.textPrimary)
                    meta
                        .wrTextStyle(.bodySmall)
                        .foregroundStyle(WrTheme.Colors.textTertiary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                badge

                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(WrTheme.Colors.iconTertiary)
            }
            .padding(.horizontal, WrTheme.Space.s200)
            .padding(.vertical, WrTheme.Space.s150)
            .background(isHovered ? WrTheme.Colors.bgHover : .clear)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { isHovered = $0 }
    }
}

/// `v4/EmptyState`.
public struct WrEmptyState<Actions: View>: View {
    private let systemImage: String
    private let title: LocalizedStringKey
    private let description: LocalizedStringKey?
    @ViewBuilder private let actions: Actions

    public init(
        systemImage: String,
        title: LocalizedStringKey,
        description: LocalizedStringKey? = nil,
        @ViewBuilder actions: () -> Actions = { EmptyView() }
    ) {
        self.systemImage = systemImage
        self.title = title
        self.description = description
        self.actions = actions()
    }

    public var body: some View {
        VStack(spacing: WrTheme.Space.s100) {
            Image(systemName: systemImage)
                .font(.system(size: 20, weight: .medium))
                .foregroundStyle(WrTheme.Colors.iconSecondary)
                .frame(width: 48, height: 48)
                .background(WrTheme.Colors.bgHover, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
                .padding(.bottom, WrTheme.Space.s100)
            Text(title)
                .wrTextStyle(.headingSmall)
                .foregroundStyle(WrTheme.Colors.textPrimary)
            if let description {
                Text(description)
                    .wrTextStyle(.bodySmall)
                    .foregroundStyle(WrTheme.Colors.textSecondary)
            }
            HStack(spacing: WrTheme.Space.s100) {
                actions
            }
            .padding(.top, WrTheme.Space.s100)
        }
        .multilineTextAlignment(.center)
        .padding(.vertical, WrTheme.Space.s600)
        .padding(.horizontal, WrTheme.Space.s300)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - Space card

/// `v4/SpaceCard`: one of the two spaces of "Choose your space". When the pointer is over a
/// card it's highlighted and its action turns primary; the screen dims the other card.
public struct WrSpaceCard: View {
    public enum Tone: Sendable {
        /// Private space: neutral icon tile.
        case neutral
        /// Open space: accent icon tile.
        case accent
    }

    private let eyebrow: LocalizedStringKey
    private let title: LocalizedStringKey
    private let description: LocalizedStringKey
    private let systemImage: String
    private let tone: Tone
    private let status: WrPill
    private let models: [String]
    private let benefits: [LocalizedStringKey]
    private let actionTitle: LocalizedStringKey
    private let footnote: LocalizedStringKey
    private let isHighlighted: Bool
    private let isDimmed: Bool
    private let actionIdentifier: String
    private let onHover: (Bool) -> Void
    private let action: () -> Void

    public init(
        eyebrow: LocalizedStringKey,
        title: LocalizedStringKey,
        description: LocalizedStringKey,
        systemImage: String,
        tone: Tone,
        status: WrPill,
        models: [String],
        benefits: [LocalizedStringKey],
        actionTitle: LocalizedStringKey,
        footnote: LocalizedStringKey,
        isHighlighted: Bool,
        isDimmed: Bool,
        actionIdentifier: String,
        onHover: @escaping (Bool) -> Void = { _ in },
        action: @escaping () -> Void
    ) {
        self.eyebrow = eyebrow
        self.title = title
        self.description = description
        self.systemImage = systemImage
        self.tone = tone
        self.status = status
        self.models = models
        self.benefits = benefits
        self.actionTitle = actionTitle
        self.footnote = footnote
        self.isHighlighted = isHighlighted
        self.isDimmed = isDimmed
        self.actionIdentifier = actionIdentifier
        self.onHover = onHover
        self.action = action
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Image(systemName: systemImage)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(tone == .accent ? WrTheme.Colors.accent : WrTheme.Colors.iconPrimary)
                    .frame(width: 40, height: 40)
                    .background(tone == .accent ? WrTheme.Colors.accentSubtle : WrTheme.Colors.bgHover, in: RoundedRectangle(cornerRadius: WrTheme.Radius.lg))
                Spacer()
                status
            }

            WrTitleBlock(
                eyebrow: eyebrow,
                title: title,
                subtitle: description,
                eyebrowColor: tone == .accent ? WrTheme.Colors.accent : WrTheme.Colors.textTertiary
            )
            .padding(.top, WrTheme.Space.s300)

            if !models.isEmpty {
                Text("AI models")
                    .textCase(.uppercase)
                    .wrTextStyle(.overline)
                    .foregroundStyle(WrTheme.Colors.textTertiary)
                    .padding(.top, WrTheme.Space.s300)
                HStack(spacing: WrTheme.Space.s100) {
                    ForEach(models, id: \.self) { WrChip($0) }
                }
                .padding(.top, WrTheme.Space.s100)
            }

            WrDivider()
                .padding(.vertical, WrTheme.Space.s300)

            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(benefits.enumerated()), id: \.offset) { WrCheckItem($0.element) }
            }

            Spacer(minLength: WrTheme.Space.s400)

            WrButton(actionTitle, kind: isHighlighted ? .primary : .secondary, fillsWidth: true, action: action)
                .accessibilityIdentifier(actionIdentifier)

            Text(footnote)
                .wrTextStyle(.bodySmall)
                .foregroundStyle(WrTheme.Colors.textTertiary)
                .frame(maxWidth: .infinity)
                .padding(.top, WrTheme.Space.s100)
        }
        .padding(WrTheme.Space.s400)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(WrTheme.Colors.bgRaised, in: shape)
        .overlay {
            shape.strokeBorder(isHighlighted ? WrTheme.Colors.accent : WrTheme.Colors.borderDefault, lineWidth: isHighlighted ? 2 : 1)
        }
        .wrElevation(isHighlighted ? .dialog : .raised)
        .opacity(isDimmed ? 0.6 : 1)
        .contentShape(shape)
        .onTapGesture(perform: action)
        .onHover(perform: onHover)
        .accessibilityElement(children: .contain)
        .animation(.easeInOut(duration: 0.2), value: isHighlighted)
        .animation(.easeInOut(duration: 0.2), value: isDimmed)
    }

    private var shape: RoundedRectangle {
        RoundedRectangle(cornerRadius: WrTheme.Radius.xl)
    }
}
