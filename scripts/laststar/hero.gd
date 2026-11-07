extends CharacterBody3D

@export var walk_speed := 5.8
@export var sprint_speed := 8.6
@export var jump_speed := 7.0
@export var gravity := 20.0
@export var world_root: Node3D

var move_input := Vector2.ZERO
var camera_yaw := 0.0
var camera_pitch := -0.24
var sprinting := false
var jump_requested := false
var skill_cooldown := 0.0
var attack_cooldown := 0.0
var health := 100
var energy := 100
var energy_regen_remainder := 0.0
var camera_pivot: Node3D
var spring_arm: SpringArm3D


func _ready() -> void:
	_build_character()
	_build_camera()


func _build_character() -> void:
	var shape := CapsuleShape3D.new()
	shape.radius = 0.42
	shape.height = 1.9
	var collider := CollisionShape3D.new()
	collider.shape = shape
	collider.position.y = 0.94
	add_child(collider)
	var capsule := CapsuleMesh.new()
	capsule.radius = 0.38
	capsule.height = 1.75
	var body_material := StandardMaterial3D.new()
	body_material.albedo_color = Color("#4178b7")
	body_material.roughness = 0.8
	var body := MeshInstance3D.new()
	body.mesh = capsule
	body.material_override = body_material
	body.position.y = 0.94
	add_child(body)
	var head := MeshInstance3D.new()
	var head_mesh := SphereMesh.new()
	head_mesh.radius = 0.28
	head_mesh.height = 0.56
	head.mesh = head_mesh
	var skin := StandardMaterial3D.new()
	skin.albedo_color = Color("#eac89e")
	head.material_override = skin
	head.position = Vector3(0.0, 2.0, 0.0)
	add_child(head)
	var scarf := MeshInstance3D.new()
	var scarf_mesh := BoxMesh.new()
	scarf_mesh.size = Vector3(0.55, 0.16, 0.62)
	scarf.mesh = scarf_mesh
	var scarf_material := StandardMaterial3D.new()
	scarf_material.albedo_color = Color("#e5a34f")
	scarf.material_override = scarf_material
	scarf.position = Vector3(0.0, 1.54, 0.0)
	add_child(scarf)


func _build_camera() -> void:
	camera_pivot = Node3D.new()
	camera_pivot.name = "CameraPivot"
	camera_pivot.position = Vector3(0.0, 1.55, 0.0)
	add_child(camera_pivot)
	spring_arm = SpringArm3D.new()
	spring_arm.spring_length = 7.0
	spring_arm.margin = 0.3
	spring_arm.rotation.x = camera_pitch
	camera_pivot.add_child(spring_arm)
	var camera := Camera3D.new()
	camera.current = true
	camera.fov = 68.0
	spring_arm.add_child(camera)


func _physics_process(delta: float) -> void:
	energy_regen_remainder += 6.0 * delta
	if energy_regen_remainder >= 1.0:
		var restored := int(energy_regen_remainder)
		energy = mini(100, energy + restored)
		energy_regen_remainder -= restored
	skill_cooldown = maxf(0.0, skill_cooldown - delta)
	attack_cooldown = maxf(0.0, attack_cooldown - delta)
	var keyboard := Input.get_vector("move_left", "move_right", "move_forward", "move_back")
	var input_vector := move_input if move_input.length() > 0.06 else keyboard
	var local_direction := Vector3(input_vector.x, 0.0, input_vector.y)
	var world_direction := Basis(Vector3.UP, camera_yaw) * local_direction
	world_direction.y = 0.0
	world_direction = world_direction.normalized()
	var speed := sprint_speed if sprinting else walk_speed
	velocity.x = move_toward(velocity.x, world_direction.x * speed, 22.0 * delta)
	velocity.z = move_toward(velocity.z, world_direction.z * speed, 22.0 * delta)
	if not is_on_floor():
		velocity.y -= gravity * delta
	elif jump_requested or Input.is_action_just_pressed("jump"):
		velocity.y = jump_speed
	jump_requested = false
	if world_direction.length_squared() > 0.02:
		var target_yaw := atan2(-world_direction.x, -world_direction.z)
		rotation.y = lerp_angle(rotation.y, target_yaw, minf(1.0, delta * 12.0))
	move_and_slide()
	if is_instance_valid(world_root) and world_root.has_method("height_at"):
		var floor_y: float = world_root.call("height_at", position.x, position.z)
		if position.y < floor_y - 2.0:
			position.y = floor_y + 1.2
			velocity.y = 0.0


func _unhandled_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.button_index == MOUSE_BUTTON_LEFT and event.pressed:
		Input.mouse_mode = Input.MOUSE_MODE_CAPTURED
		return
	if Input.mouse_mode == Input.MOUSE_MODE_CAPTURED and event is InputEventMouseMotion:
		rotate_camera(event.relative)
	elif event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		Input.mouse_mode = Input.MOUSE_MODE_VISIBLE


func set_move_input(value: Vector2) -> void:
	move_input = value.limit_length(1.0)


func request_jump() -> void:
	jump_requested = true


func set_sprinting(enabled: bool) -> void:
	sprinting = enabled


func rotate_camera(delta_pixels: Vector2) -> void:
	camera_yaw -= delta_pixels.x * 0.006
	camera_pitch = clampf(camera_pitch - delta_pixels.y * 0.003, -0.9, 0.15)
	if is_instance_valid(camera_pivot):
		camera_pivot.rotation.y = camera_yaw
		spring_arm.rotation.x = camera_pitch


func attack() -> void:
	if attack_cooldown > 0.0:
		return
	attack_cooldown = 0.42
	if is_instance_valid(world_root):
		world_root.call("damage_nearest_enemy", 1, 3.2)


func cast_skill() -> void:
	if skill_cooldown > 0.0 or energy < 25:
		return
	skill_cooldown = 4.0
	energy -= 25
	if is_instance_valid(world_root):
		world_root.call("damage_nearest_enemy", 3, 7.0)
