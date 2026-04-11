package io.writeopia.drawing.di

import io.writeopia.drawing.viewmodel.DrawingViewModel

class DrawingInjection {

    fun provideDrawingViewModel(): DrawingViewModel {
        return DrawingViewModel()
    }
}
