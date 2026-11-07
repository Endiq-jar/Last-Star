extends CanvasLayer

signal move_changed(value: Vector2)
signal camera_drag(delta_pixels: Vector2)
signal action_pressed(action_name: String)

var overlay: Control
var status_label: Label
var vitals_label: Label
var stick_center := Vector2.ZERO
var stick_offset := Vector2.ZERO
var active_move_touch := -1
var active_camera_touch := -1
var viewport_size := Vector2(960.0, 540.0)
var buttons: Array[Button] = []


func _ready() -> void:
	overlay = Control.new()
	overlay.name = "TouchOverlay"
	overlay.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	overlay.mouse_filter = Control.MOUSE_FILTER_IGNORE
	overlay.draw.connect(_draw_overlay)
	add_child(overlay)
	vitals_label = Label.new()
	vitals_label.text = "HP 100     ENERGY 100"
	vitals_label.add_theme_font_size_override("font_size", 22)
	vitals_label.add_theme_color_override("font_color", Color("#f5f0ff"))
	vitals_label.add_theme_color_override("font_shadow_color", Color("#1e1e76"))
	vitals_label.add_theme_constant_override("shadow_offset_x", 2)
	vitals_label.add_theme_constant_override("shadow_offset_y", 2)
	overlay.add_child(vitals_label)
	status_label = Label.new()
	status_label.text = "RIDGE TOWN  •  EXPLORE THE VALLEY"
	status_label.add_theme_font_size_override("font_size", 17)
	status_label.add_theme_color_override("font_color", Color("#f5f0ff"))
	status_label.add_theme_color_override("font_shadow_color", Color("#1e1e76"))
	status_label.add_theme_constant_override("shadow_offset_x", 2)
	status_label.add_theme_constant_override("shadow_offset_y", 2)
	overlay.add_child(status_label)
	_create_button("RUN", "sprint", Vector2(-104, -315), true)
	_create_button("SKILL", "skill", Vector2(-98, -220))
	_create_button("JUMP", "jump", Vector2(-194, -126))
	_create_button("ATK", "attack", Vector2(-98, -116))
	get_viewport().size_changed.connect(_layout)
	overlay.resized.connect(_layout)
	_layout()


func _create_button(caption: String, action: String, offset: Vector2, hold := false) -> void:
	var button := Button.new()
	button.text = caption
	button.custom_minimum_size = Vector2(82.0, 70.0)
	button.add_theme_font_size_override("font_size", 17)
	var label_color := Color("#f5f0ff")
	button.add_theme_color_override("font_hover_color", Color.WHITE)
	var normal := StyleBoxFlat.new()
	normal.bg_color = Color("#4b4bc3").lerp(Color("#707ff5"), 0.35)
	if action == "attack":
		normal.bg_color = Color("#f7b769")
		label_color = Color("#1e1e76")
	elif action == "skill":
		normal.bg_color = Color("#f2a1f2")
		label_color = Color("#1e1e76")
	button.add_theme_color_override("font_color", label_color)
	button.add_theme_color_override("font_pressed_color", label_color)
	normal.border_color = Color("#a195f9")
	normal.set_border_width_all(2)
	normal.set_corner_radius_all(24)
	button.add_theme_stylebox_override("normal", normal)
	var pressed := normal.duplicate() as StyleBoxFlat
	pressed.bg_color = Color("#a195f9")
	button.add_theme_stylebox_override("pressed", pressed)
	button.add_theme_stylebox_override("hover", normal)
	button.set_meta("action", action)
	if hold:
		button.button_down.connect(func(): action_pressed.emit(action))
		button.button_up.connect(func(): action_pressed.emit("sprint_release"))
	else:
		button.pressed.connect(func(): action_pressed.emit(action))
	overlay.add_child(button)
	button.set_meta("offset", offset)
	buttons.append(button)


func _layout() -> void:
	if not is_instance_valid(overlay):
		return
	viewport_size = get_viewport().get_visible_rect().size
	overlay.size = viewport_size
	vitals_label.position = Vector2(28.0, 22.0)
	status_label.position = Vector2(28.0, 54.0)
	for button in buttons:
		var offset: Vector2 = button.get_meta("offset")
		button.position = Vector2(viewport_size.x + offset.x, viewport_size.y + offset.y)
	stick_center = Vector2(112.0, viewport_size.y - 112.0)
	overlay.queue_redraw()


func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		var touch := event as InputEventScreenTouch
		if touch.pressed:
			if _inside_action_buttons(touch.position):
				return
			if (
				touch.position.x < viewport_size.x * 0.42
				and touch.position.y > viewport_size.y * 0.48
			):
				active_move_touch = touch.index
				stick_offset = (touch.position - stick_center).limit_length(62.0)
				move_changed.emit(
					Vector2(stick_offset.x / 62.0, -stick_offset.y / 62.0).limit_length(1.0)
				)
				overlay.queue_redraw()
				get_viewport().set_input_as_handled()
			elif touch.position.x > viewport_size.x * 0.42:
				active_camera_touch = touch.index
				get_viewport().set_input_as_handled()
		else:
			if touch.index == active_move_touch:
				active_move_touch = -1
				stick_offset = Vector2.ZERO
				move_changed.emit(Vector2.ZERO)
				overlay.queue_redraw()
				get_viewport().set_input_as_handled()
			elif touch.index == active_camera_touch:
				active_camera_touch = -1
	elif event is InputEventScreenDrag:
		var drag := event as InputEventScreenDrag
		if drag.index == active_move_touch:
			stick_offset = (drag.position - stick_center).limit_length(62.0)
			move_changed.emit(
				Vector2(stick_offset.x / 62.0, -stick_offset.y / 62.0).limit_length(1.0)
			)
			overlay.queue_redraw()
			get_viewport().set_input_as_handled()
		elif drag.index == active_camera_touch:
			camera_drag.emit(drag.relative)
			get_viewport().set_input_as_handled()


func _inside_action_buttons(point: Vector2) -> bool:
	return point.x > viewport_size.x - 240.0 and point.y > viewport_size.y - 400.0


func _draw_overlay() -> void:
	if active_move_touch >= 0:
		overlay.draw_circle(stick_center, 68.0, Color(0.12, 0.12, 0.46, 0.45))
		overlay.draw_arc(stick_center, 68.0, 0.0, TAU, 48, Color(0.75, 0.72, 1.0, 0.8), 3.0)
		overlay.draw_circle(stick_center + stick_offset, 27.0, Color(0.95, 0.82, 0.58, 0.9))


func set_world_status(text: String) -> void:
	if is_instance_valid(status_label):
		status_label.text = text


func set_vitals(health: int, energy: int) -> void:
	if is_instance_valid(vitals_label):
		vitals_label.text = "HP %d     ENERGY %d" % [health, energy]
