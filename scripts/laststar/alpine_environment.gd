extends RefCounted

const ROCK_TEXTURE: Texture2D = preload(
	"res://assets/terrain3d/demo_assets/textures/rock023_alb_ht.png"
)
const WOOD_MATERIAL: Material = preload("res://assets/terrain3d/foliage/pine_bark.tres")


static func _height(height_func: Callable, x: float, z: float) -> float:
	return float(height_func.call(x, z))


static func build(root: Node3D, height_func: Callable) -> void:
	_build_alpine_trail(root, height_func)
	_build_lodge(root, height_func)
	_build_mountain_backdrop(root, height_func)


static func _build_alpine_trail(root: Node3D, height_func: Callable) -> void:
	var material := StandardMaterial3D.new()
	material.albedo_texture = ROCK_TEXTURE
	material.roughness = 1.0
	material.uv1_scale = Vector3(0.32, 0.32, 1.0)
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	const STEPS := 36
	for i in range(STEPS + 1):
		var t := float(i) / float(STEPS)
		var z := lerpf(5.0, -54.0, t)
		var center_x := 2.4 * sin((z + 54.0) * 0.052) + 0.8 * sin(z * 0.13)
		var half_width := 1.8 + 0.32 * sin(t * TAU * 2.0)
		for side in range(2):
			var edge := -1.0 if side == 0 else 1.0
			var x := center_x + half_width * edge
			var y := _height(height_func, x, z) + 0.055
			surface.set_uv(Vector2(t * 7.0, float(side)))
			surface.add_vertex(Vector3(x, y, z))
		if i > 0:
			var a := (i - 1) * 2
			var b := a + 1
			var c := i * 2
			var d := c + 1
			surface.add_index(a)
			surface.add_index(b)
			surface.add_index(c)
			surface.add_index(b)
			surface.add_index(d)
			surface.add_index(c)
	surface.generate_normals()
	var trail_mesh := surface.commit()
	var trail := MeshInstance3D.new()
	trail.name = "AlpineGravelTrail"
	trail.mesh = trail_mesh
	trail.material_override = material
	trail.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	root.add_child(trail)


static func _build_lodge(root: Node3D, height_func: Callable) -> void:
	var lodge := Node3D.new()
	lodge.name = "PineRidgeLodge"
	var x := -2.0
	var z := -46.0
	lodge.position = Vector3(x, _height(height_func, x, z), z)
	root.add_child(lodge)
	var wood := WOOD_MATERIAL
	var stone := StandardMaterial3D.new()
	stone.albedo_texture = ROCK_TEXTURE
	stone.roughness = 1.0
	var dark_wood := StandardMaterial3D.new()
	dark_wood.albedo_color = Color("#30251d")
	dark_wood.roughness = 0.95
	var glass := StandardMaterial3D.new()
	glass.albedo_color = Color("#243a3a")
	glass.roughness = 0.28
	glass.metallic = 0.08

	# Raised stone foundation and separate timber wall sections leave a clear doorway.
	_add_lodge_box(lodge, "StoneFoundation", Vector3(8.1, 0.72, 7.4), Vector3(0, 0.36, 0), stone)
	_add_lodge_box(lodge, "BackWall", Vector3(7.2, 3.1, 0.34), Vector3(0, 2.25, -3.25), wood)
	_add_lodge_box(lodge, "LeftWall", Vector3(0.34, 3.1, 6.2), Vector3(-3.43, 2.25, 0), wood)
	_add_lodge_box(lodge, "RightWall", Vector3(0.34, 3.1, 6.2), Vector3(3.43, 2.25, 0), wood)
	for side in [-1.0, 1.0]:
		var window_x := side * 2.28
		_add_lodge_box(
			lodge,
			"FrontWindowSillWall",
			Vector3(2.6, 1.55, 0.34),
			Vector3(window_x, 0.775, 3.25),
			wood
		)
		_add_lodge_box(
			lodge,
			"FrontWindowHeader",
			Vector3(2.6, 0.48, 0.34),
			Vector3(window_x, 2.86, 3.25),
			wood
		)
		_add_lodge_box(
			lodge,
			"FrontWindowOuterPier",
			Vector3(0.5, 0.92, 0.34),
			Vector3(side * 3.33, 2.14, 3.25),
			wood
		)
		_add_lodge_box(
			lodge,
			"FrontWindowInnerPier",
			Vector3(0.5, 0.92, 0.34),
			Vector3(side * 1.23, 2.14, 3.25),
			wood
		)
	_add_lodge_box(lodge, "DoorHeader", Vector3(2.0, 0.48, 0.42), Vector3(0, 3.56, 3.22), wood)
	_add_lodge_box(lodge, "Door", Vector3(1.55, 2.55, 0.12), Vector3(0, 1.72, 3.48), dark_wood)
	_add_lodge_box(
		lodge, "WindowLeftGlass", Vector3(1.05, 0.92, 0.08), Vector3(-2.28, 2.14, 3.48), glass
	)
	_add_lodge_box(
		lodge, "WindowRightGlass", Vector3(1.05, 0.92, 0.08), Vector3(2.28, 2.14, 3.48), glass
	)
	for window_x in [-2.28, 2.28]:
		for frame_x in [-0.61, 0.61]:
			_add_lodge_box(
				lodge,
				"WindowFrame",
				Vector3(0.12, 1.12, 0.16),
				Vector3(window_x + frame_x, 2.14, 3.55),
				wood
			)
		for frame_y in [1.62, 2.66]:
			_add_lodge_box(
				lodge,
				"WindowFrame",
				Vector3(1.32, 0.12, 0.16),
				Vector3(window_x, frame_y, 3.55),
				wood
			)

	# Chunky exposed log ends and roof beams give the lodge a hand-built silhouette.
	var log_mesh := CylinderMesh.new()
	log_mesh.top_radius = 0.16
	log_mesh.bottom_radius = 0.19
	log_mesh.height = 6.5
	log_mesh.radial_segments = 8
	for level in range(8):
		for side in [-1.0, 1.0]:
			var log := MeshInstance3D.new()
			log.mesh = log_mesh
			log.material_override = wood
			log.rotation.x = PI * 0.5
			log.position = Vector3(side * 3.48, 0.96 + float(level) * 0.37, 0)
			lodge.add_child(log)
	for front_z in [-3.42, 3.42]:
		for beam_y in [0.92, 3.2]:
			var beam := MeshInstance3D.new()
			beam.mesh = log_mesh
			beam.material_override = wood
			beam.rotation.z = PI * 0.5
			beam.position = Vector3(0, beam_y, front_z)
			lodge.add_child(beam)
	var roof := StandardMaterial3D.new()
	roof.albedo_color = Color("#48483c")
	roof.albedo_texture = ROCK_TEXTURE
	roof.roughness = 0.94
	for side in [-1.0, 1.0]:
		var roof_panel := MeshInstance3D.new()
		var panel_mesh := BoxMesh.new()
		panel_mesh.size = Vector3(4.65, 0.2, 7.75)
		roof_panel.mesh = panel_mesh
		roof_panel.material_override = roof
		roof_panel.position = Vector3(side * 1.72, 4.74, 0)
		roof_panel.rotation.z = -side * 0.59
		lodge.add_child(roof_panel)
	_add_lodge_box(lodge, "RidgeBeam", Vector3(0.24, 0.28, 8.0), Vector3(0, 6.08, 0), wood)
	_add_lodge_gable(lodge, wood, 3.55, 2.38, 3.28)
	_add_lodge_gable(lodge, wood, 3.55, 2.38, -3.28)
	for step in range(3):
		_add_lodge_box(
			lodge,
			"EntryStep",
			Vector3(2.25, 0.22, 0.68),
			Vector3(0, 0.76 - float(step) * 0.18, 3.98 + float(step) * 0.6),
			stone
		)
	# Solid, doorway-aware collision keeps the cabin decorative but enterable.
	var collision_root := StaticBody3D.new()
	collision_root.name = "LodgeCollision"
	for piece in [
		[Vector3(0.34, 3.1, 6.2), Vector3(-3.43, 2.25, 0)],
		[Vector3(0.34, 3.1, 6.2), Vector3(3.43, 2.25, 0)],
		[Vector3(7.2, 3.1, 0.34), Vector3(0, 2.25, -3.25)],
		[Vector3(2.6, 3.1, 0.34), Vector3(-2.28, 2.25, 3.25)],
		[Vector3(2.6, 3.1, 0.34), Vector3(2.28, 2.25, 3.25)],
	]:
		var shape_node := CollisionShape3D.new()
		var shape := BoxShape3D.new()
		shape.size = piece[0]
		shape_node.shape = shape
		shape_node.position = piece[1]
		collision_root.add_child(shape_node)
	lodge.add_child(collision_root)


static func _add_lodge_box(
	parent: Node3D, node_name: String, size: Vector3, at: Vector3, material: Material
) -> void:
	var instance := MeshInstance3D.new()
	instance.name = node_name
	var mesh := BoxMesh.new()
	mesh.size = size
	instance.mesh = mesh
	instance.material_override = material
	instance.position = at
	parent.add_child(instance)


static func _add_lodge_gable(
	parent: Node3D, material: Material, half_width: float, height: float, at_z: float
) -> void:
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	var front := [
		Vector3(-half_width, 3.82, at_z),
		Vector3(half_width, 3.82, at_z),
		Vector3(0, 3.82 + height, at_z)
	]
	var back := [
		Vector3(-half_width, 3.82, at_z),
		Vector3(0, 3.82 + height, at_z),
		Vector3(half_width, 3.82, at_z)
	]
	for points in [front, back]:
		for point in points:
			surface.set_uv(Vector2(point.x * 0.18, point.y * 0.22))
			surface.add_vertex(point)
	surface.generate_normals()
	var mesh := surface.commit()
	var instance := MeshInstance3D.new()
	instance.mesh = mesh
	instance.material_override = material
	parent.add_child(instance)


static func _build_mountain_backdrop(root: Node3D, height_func: Callable) -> void:
	var rock := StandardMaterial3D.new()
	rock.albedo_texture = ROCK_TEXTURE
	rock.roughness = 1.0
	rock.cull_mode = BaseMaterial3D.CULL_DISABLED
	var peaks := [
		[Vector2(-88.0, -94.0), 44.0, 68.0, 7],
		[Vector2(-48.0, -124.0), 58.0, 104.0, 9],
		[Vector2(5.0, -146.0), 66.0, 112.0, 8],
		[Vector2(73.0, -122.0), 52.0, 91.0, 9],
		[Vector2(118.0, -93.0), 38.0, 64.0, 7],
	]
	for i in range(peaks.size()):
		var entry: Array = peaks[i]
		_add_faceted_peak(
			root,
			height_func,
			entry[0],
			float(entry[1]),
			float(entry[2]),
			int(entry[3]),
			i * 431 + 97,
			rock
		)


static func _add_faceted_peak(
	root: Node3D,
	height_func: Callable,
	center: Vector2,
	radius: float,
	peak_height: float,
	segments: int,
	seed: int,
	material: Material
) -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = seed
	var rings: Array = []
	for ring_index in range(3):
		var ring: Array[Vector3] = []
		var ring_radius: float = radius * float([1.0, 0.62, 0.28][ring_index])
		var ring_height: float = peak_height * float([0.0, 0.42, 0.76][ring_index])
		var phase := rng.randf_range(0.0, TAU)
		for i in range(segments):
			var angle := phase + TAU * float(i) / float(segments)
			var roughness := rng.randf_range(0.78, 1.18)
			ring.append(
				Vector3(
					cos(angle) * ring_radius * roughness,
					ring_height + rng.randf_range(-2.0, 2.0),
					sin(angle) * ring_radius * roughness
				)
			)
		rings.append(ring)
	var surface := SurfaceTool.new()
	surface.begin(Mesh.PRIMITIVE_TRIANGLES)
	for ring_index in range(2):
		var lower: Array = rings[ring_index]
		var upper: Array = rings[ring_index + 1]
		for i in range(segments):
			var next := (i + 1) % segments
			for point in [lower[i], upper[i], lower[next], lower[next], upper[i], upper[next]]:
				surface.set_uv(Vector2(point.x * 0.08, point.z * 0.08))
				surface.add_vertex(point)
	var apex := Vector3(
		rng.randf_range(-radius * 0.12, radius * 0.12),
		peak_height,
		rng.randf_range(-radius * 0.12, radius * 0.12)
	)
	var top_ring: Array = rings[2]
	for i in range(segments):
		for point in [top_ring[i], apex, top_ring[(i + 1) % segments]]:
			surface.set_uv(Vector2(point.x * 0.08, point.z * 0.08))
			surface.add_vertex(point)
	surface.generate_normals()
	var mesh := surface.commit()
	var peak := MeshInstance3D.new()
	peak.name = "AlpinePeak_%d" % seed
	peak.mesh = mesh
	peak.material_override = material
	peak.position = Vector3(center.x, _height(height_func, center.x, center.y) - 1.0, center.y)
	peak.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	root.add_child(peak)
