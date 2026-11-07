extends CharacterBody3D

@export var target: Node3D
@export var health := 6
@export var speed := 2.0

var gravity := 20.0
var think_timer := 0.0
var body_mesh: MeshInstance3D


func _ready() -> void:
	var shape := CapsuleShape3D.new()
	shape.radius = 0.62
	shape.height = 1.8
	var collider := CollisionShape3D.new()
	collider.shape = shape
	collider.position.y = 0.9
	add_child(collider)
	body_mesh = MeshInstance3D.new()
	var orb := SphereMesh.new()
	orb.radius = 0.72
	orb.height = 1.44
	body_mesh.mesh = orb
	var material := StandardMaterial3D.new()
	material.albedo_color = Color("#7856aa")
	material.emission_enabled = true
	material.emission = Color("#38265e")
	material.roughness = 0.42
	body_mesh.material_override = material
	body_mesh.position.y = 1.0
	add_child(body_mesh)
	var eye := MeshInstance3D.new()
	var eye_mesh := SphereMesh.new()
	eye_mesh.radius = 0.2
	eye_mesh.height = 0.4
	eye.mesh = eye_mesh
	var eye_material := StandardMaterial3D.new()
	eye_material.albedo_color = Color("#f1c869")
	eye_material.emission_enabled = true
	eye_material.emission = Color("#e9a72f")
	eye.material_override = eye_material
	eye.position = Vector3(0.0, 1.1, -0.57)
	add_child(eye)


func _physics_process(delta: float) -> void:
	think_timer -= delta
	if is_instance_valid(target) and think_timer <= 0.0:
		think_timer = 0.12
		var offset := target.global_position - global_position
		if offset.length() < 15.0 and offset.length() > 2.0:
			var direction := Vector3(offset.x, 0.0, offset.z).normalized()
			velocity.x = direction.x * speed
			velocity.z = direction.z * speed
			rotation.y = atan2(-direction.x, -direction.z)
		else:
			velocity.x = move_toward(velocity.x, 0.0, 4.0 * delta)
			velocity.z = move_toward(velocity.z, 0.0, 4.0 * delta)
	if not is_on_floor():
		velocity.y -= gravity * delta
	else:
		velocity.y = 0.0
	move_and_slide()


func take_damage(amount: int) -> void:
	health -= amount
	if is_instance_valid(body_mesh):
		var tween := create_tween()
		tween.tween_property(body_mesh, "scale", Vector3(1.16, 0.8, 1.16), 0.08)
		tween.tween_property(body_mesh, "scale", Vector3.ONE, 0.12)
