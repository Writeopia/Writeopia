import Observation
import WrModels
import WrStorage

/// How folders and documents are shown and sorted, like the arrangement and sorting options of
/// the Compose notes menu. Shared by every folder of the documents tab and kept between launches.
@Observable
final class FolderDisplaySettings {
    var arrangement: DocumentsArrangement {
        didSet { preferences?.set(arrangement.rawValue, for: .documentsArrangement) }
    }

    var order: DocumentsOrder {
        didSet { preferences?.set(order.rawValue, for: .documentsOrder) }
    }

    @ObservationIgnored private let preferences: Preferences?

    init(preferences: Preferences?) {
        self.preferences = preferences
        arrangement = preferences?.string(.documentsArrangement).flatMap(DocumentsArrangement.init(rawValue:)) ?? .grid
        order = preferences?.string(.documentsOrder).flatMap(DocumentsOrder.init(rawValue:)) ?? .updated
    }
}

extension DocumentsArrangement {
    var title: String {
        switch self {
        case .list: String(localized: "List")
        case .grid: String(localized: "Grid")
        case .staggeredGrid: String(localized: "Staggered grid")
        }
    }

    var systemImage: String {
        switch self {
        case .list: "list.bullet"
        case .grid: "square.grid.2x2"
        case .staggeredGrid: "rectangle.3.group"
        }
    }
}

extension DocumentsOrder {
    var title: String {
        switch self {
        case .updated: String(localized: "Last updated")
        case .created: String(localized: "Creation date")
        case .name: String(localized: "Name")
        }
    }

    var systemImage: String {
        switch self {
        case .updated: "clock.arrow.circlepath"
        case .created: "calendar.badge.plus"
        case .name: "textformat.abc"
        }
    }
}
