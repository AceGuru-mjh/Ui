package com.foundry.preview.engine

import com.foundry.preview.engine.renderers.*

/**
 * 注册所有内置组件渲染器。应在 Application.onCreate 中调用一次。
 * 新增组件只需：实现 ComponentRenderer + 在此处 register。
 */
fun initializeRenderers() {
    ComponentRegistry.register(TextRenderer())
    ComponentRegistry.register(ColumnRenderer())
    ComponentRegistry.register(RowRenderer())
    ComponentRegistry.register(BoxRenderer())
    ComponentRegistry.register(ButtonRenderer())
    ComponentRegistry.register(SpacerRenderer())
    ComponentRegistry.register(CardRenderer())
    ComponentRegistry.register(DividerRenderer())
    ComponentRegistry.register(ImageRenderer())
    ComponentRegistry.register(TextFieldRenderer())
    ComponentRegistry.register(ScrollRenderer())
    ComponentRegistry.register(SurfaceRenderer())
    ComponentRegistry.register(LazyColumnRenderer())
    ComponentRegistry.register(SwitchRenderer())
    ComponentRegistry.register(CheckboxRenderer())
    ComponentRegistry.register(SliderRenderer())
    ComponentRegistry.register(ProgressIndicatorRenderer())
    ComponentRegistry.register(TabRowRenderer())
    ComponentRegistry.register(RadioButtonRenderer())
    ComponentRegistry.register(SpinnerRenderer())
    ComponentRegistry.register(ChipRenderer())
    ComponentRegistry.register(NavigationBarRenderer())
    ComponentRegistry.register(AlertDialogRenderer())
    ComponentRegistry.register(BadgeRenderer())
    ComponentRegistry.register(RuntimeViewRenderer())
    ComponentRegistry.register(DrawerRenderer())
    ComponentRegistry.register(SnackbarRenderer())
    ComponentRegistry.register(DropdownMenuRenderer())
    ComponentRegistry.register(BottomSheetRenderer())
    ComponentRegistry.register(ListItemRenderer())
    ComponentRegistry.register(RangeSliderRenderer())
    ComponentRegistry.register(SearchBarRenderer())
    ComponentRegistry.register(NavigationRailRenderer())
    ComponentRegistry.register(SegmentedButtonRenderer())
    ComponentRegistry.register(TopAppBarRenderer())
}
