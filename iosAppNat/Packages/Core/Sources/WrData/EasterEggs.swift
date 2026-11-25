import Foundation
import WrModels

/// Hidden documents, like `EasterEggs` of the `tutorials` module of the Compose app: tapping the
/// version of the app ten times opens the document about the writer the version is named after.
public enum EasterEggs {
    public static let versionWriterTitle = "Manuel Bandeira"

    /// The steps of the document about the writer of the current version. The texts are the ones
    /// of the Compose app, which keeps them in English.
    public static func versionWriterSteps() -> [StoryStep] {
        var steps = [
            StoryStep(
                type: .title,
                text: versionWriterTitle,
                position: Rank.first,
                decoration: Decoration(backgroundColor: -4_144_960)
            ),
        ]

        for (index, paragraph) in versionWriterParagraphs.enumerated() {
            steps.append(
                StoryStep(
                    type: .message,
                    text: paragraph.text,
                    tags: paragraph.heading ? [TagInfo(tag: "H2")] : [],
                    position: Rank.at(index + 1)
                )
            )
        }
        return steps
    }

    private struct Paragraph {
        let text: String
        var heading = false
    }

    private static let versionWriterParagraphs: [Paragraph] = [
        Paragraph(text: "Manuel Carneiro de Sousa Bandeira Filho was born in Recife on April 19, 1886, and died in Rio de Janeiro on October 13, 1968. He is one of the great poets of Brazilian Modernism, and also a translator, literary critic, chronicler and professor."),
        Paragraph(text: "A life with a short horizon", heading: true),
        Paragraph(text: "As a young man he studied architecture in São Paulo, but at about 18 he was diagnosed with tuberculosis, which in those days was a sentence. He left his studies and spent years looking for a cure in sanatoriums and in the mountains, always expecting an early death. He lived for more than 80 years. That closeness to death, treated with irony and tenderness instead of drama, marks much of his work."),
        Paragraph(text: "The poet", heading: true),
        Paragraph(text: "His first books, Cinza das Horas (1917), Carnaval (1919) and O Ritmo Dissoluto (1924), still carry the weight of the Symbolists and Parnassians. In 1922 his poem Os Sapos, a satire of the Parnassian poets, was read at the Week of Modern Art in São Paulo, and it became an anthem of the movement. With Libertinagem (1930) he found his own voice: free verse, everyday subjects, colloquial speech and humor."),
        Paragraph(text: "Later books include Estrela da Manhã (1936), Lira dos Cinquent'Anos (1940), Belo Belo (1948) and Opus 10 (1952). His memoir Itinerário de Pasárgada (1954) tells how the poems came to be."),
        Paragraph(text: "Pasárgada", heading: true),
        Paragraph(text: "Pasárgada was an ancient Persian capital, which Bandeira read about as a boy. In Vou-me Embora pra Pasárgada it became a place of imagination, where the poet is a friend of the king and can have whatever he wants. It is one of the best-known Brazilian poems, and “going to Pasárgada” means escaping to a place where everything is possible."),
        Paragraph(text: "Other work and recognition", heading: true),
        Paragraph(text: "Bandeira translated poetry and plays from Spanish, German, English and French, wrote chronicles and criticism, and taught Brazilian literature at the Colégio Pedro II and at the Faculdade Nacional de Filosofia. He joined the Brazilian Academy of Letters in 1940, in seat 24."),
        Paragraph(text: "Why him?", heading: true),
        Paragraph(text: "Writing is an act of paying attention to small things, such as a street in Recife, a sad afternoon, the daily news. Bandeira showed that a plain sentence can be enough. That fits an editor that wants to stay out of the way of the words. Congratulations on finding this page!"),
    ]

    /// Creates the document in the root of the workspace of `repository` and returns it.
    public static func createVersionWriterDocument(in repository: DocumentsRepository) async throws -> WrDocument {
        var document = try await repository.createDocument(title: versionWriterTitle, parentId: Folder.rootId)
        document.title = versionWriterTitle
        document.content = versionWriterSteps()
        try await repository.save(document)
        return document
    }
}
